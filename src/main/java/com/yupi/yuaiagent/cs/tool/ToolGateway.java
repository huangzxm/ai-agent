package com.yupi.yuaiagent.cs.tool;

import cn.hutool.json.JSONUtil;
import com.yupi.yuaiagent.cs.model.ToolInvocation;
import com.yupi.yuaiagent.cs.model.ToolResult;
import com.yupi.yuaiagent.cs.model.UserIdentity;
import com.yupi.yuaiagent.cs.security.AuditService;
import com.yupi.yuaiagent.cs.security.AuthService;
import com.yupi.yuaiagent.cs.security.PermissionChecker;
import com.yupi.yuaiagent.cs.support.CriticalServiceDownException;
import com.yupi.yuaiagent.cs.support.MetricsCollector;
import com.yupi.yuaiagent.cs.support.ServiceCircuitBreaker;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.stereotype.Component;

/**
 * 统一工具调用网关：熔断检查 → 鉴权 → 权限校验 → 执行 → Observation 瘦身 → 埋点审计。
 * 所有工具（本地/MCP）都必须经过这里，安全校验不会被 LLM 绕过。
 */
@Component
@Slf4j
public class ToolGateway {

    private final ToolRegistry toolRegistry;
    private final AuthService authService;
    private final PermissionChecker permissionChecker;
    private final AuditService auditService;
    private final MetricsCollector metricsCollector;
    private final ObservationSanitizer observationSanitizer;
    private final ServiceCircuitBreaker circuitBreaker;

    public ToolGateway(ToolRegistry toolRegistry, AuthService authService,
                       PermissionChecker permissionChecker, AuditService auditService,
                       MetricsCollector metricsCollector,
                       ObservationSanitizer observationSanitizer,
                       ServiceCircuitBreaker circuitBreaker) {
        this.toolRegistry = toolRegistry;
        this.authService = authService;
        this.permissionChecker = permissionChecker;
        this.auditService = auditService;
        this.metricsCollector = metricsCollector;
        this.observationSanitizer = observationSanitizer;
        this.circuitBreaker = circuitBreaker;
    }

    public ToolResult execute(ToolInvocation invocation) {
        long start = System.currentTimeMillis();
        String toolName = invocation.getToolName();
        UserIdentity identity = authService.verify(invocation.getToken());
        String userId = identity.getUserId();

        ToolCallback callback = toolRegistry.get(toolName);
        if (callback == null) {
            metricsCollector.recordTool(false);
            auditService.record(invocation.getSessionId(), userId, toolName, invocation.getArguments(), false, "NOT_FOUND");
            return ToolResult.fail(toolName, "NOT_FOUND", "工具不存在或已下线：" + toolName, cost(start));
        }

        PermissionChecker.CheckResult check = permissionChecker.check(identity, toolName, invocation.getArguments());
        if (!check.allowed()) {
            metricsCollector.recordTool(false);
            auditService.record(invocation.getSessionId(), userId, toolName, invocation.getArguments(), false, check.errorCode());
            return ToolResult.fail(toolName, check.errorCode(), check.message(), cost(start));
        }

        // 关键服务熔断：MCP/ERP 这类外部系统不可用时，直接抛出，由上层转人工，不重试
        boolean remote = "mcp".equals(toolRegistry.sourceOf(toolName));
        String serviceKey = remote ? "mcp:" + toolName : "local:" + toolName;
        if (!circuitBreaker.allow(serviceKey)) {
            metricsCollector.recordTool(false);
            auditService.record(invocation.getSessionId(), userId, toolName, invocation.getArguments(), false, "SERVICE_CIRCUIT_OPEN");
            throw new CriticalServiceDownException(serviceKey, "业务系统 " + toolName + " 暂时不可用（熔断中）");
        }

        try {
            String response = callback.call(JSONUtil.toJsonStr(invocation.getArguments()));
            circuitBreaker.recordSuccess(serviceKey);
            // Observation 瘦身：清洗后再交给 LLM，避免关键事实被系统日志噪音淹没
            ObservationSanitizer.SanitizedObservation sanitized =
                    observationSanitizer.sanitize(toolName, response);
            if (sanitized.savedChars() > 0) {
                metricsCollector.recordObservationSaved(sanitized.savedChars());
            }
            metricsCollector.recordTool(true);
            auditService.record(invocation.getSessionId(), userId, toolName, invocation.getArguments(), true, null);
            return ToolResult.ok(toolName, sanitized.content(), cost(start));
        } catch (CriticalServiceDownException e) {
            throw e;
        } catch (Exception e) {
            circuitBreaker.recordFailure(serviceKey);
            log.error("[CS] 工具执行异常 tool={} remote={} error={}", toolName, remote, e.getMessage());
            metricsCollector.recordTool(false);
            auditService.record(invocation.getSessionId(), userId, toolName, invocation.getArguments(), false, "TOOL_ERROR");
            if (remote) {
                // 外部业务系统异常 → 转人工兜底，避免 LLM 重试加剧拥堵
                throw new CriticalServiceDownException(serviceKey,
                        "业务系统 " + toolName + " 调用失败：" + e.getMessage(), e);
            }
            return ToolResult.fail(toolName, "TOOL_ERROR", "工具执行失败：" + e.getMessage(), cost(start));
        }
    }

    private long cost(long start) {
        return System.currentTimeMillis() - start;
    }
}
