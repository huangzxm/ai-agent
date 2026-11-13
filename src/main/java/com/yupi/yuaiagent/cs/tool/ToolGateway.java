package com.yupi.yuaiagent.cs.tool;

import cn.hutool.json.JSONUtil;
import com.yupi.yuaiagent.cs.model.ToolInvocation;
import com.yupi.yuaiagent.cs.model.ToolResult;
import com.yupi.yuaiagent.cs.model.UserIdentity;
import com.yupi.yuaiagent.cs.security.AuditService;
import com.yupi.yuaiagent.cs.security.AuthService;
import com.yupi.yuaiagent.cs.security.PermissionChecker;
import com.yupi.yuaiagent.cs.support.MetricsCollector;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.stereotype.Component;

/**
 * 统一工具调用网关：鉴权 → 权限校验 → 执行 → 结果标准化 → 埋点与审计。
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

    public ToolGateway(ToolRegistry toolRegistry, AuthService authService,
                       PermissionChecker permissionChecker, AuditService auditService,
                       MetricsCollector metricsCollector) {
        this.toolRegistry = toolRegistry;
        this.authService = authService;
        this.permissionChecker = permissionChecker;
        this.auditService = auditService;
        this.metricsCollector = metricsCollector;
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

        try {
            String response = callback.call(JSONUtil.toJsonStr(invocation.getArguments()));
            metricsCollector.recordTool(true);
            auditService.record(invocation.getSessionId(), userId, toolName, invocation.getArguments(), true, null);
            return ToolResult.ok(toolName, response, cost(start));
        } catch (Exception e) {
            log.error("[CS] 工具执行异常 tool={} error={}", toolName, e.getMessage());
            metricsCollector.recordTool(false);
            auditService.record(invocation.getSessionId(), userId, toolName, invocation.getArguments(), false, "TOOL_ERROR");
            return ToolResult.fail(toolName, "TOOL_ERROR", "工具执行失败：" + e.getMessage(), cost(start));
        }
    }

    private long cost(long start) {
        return System.currentTimeMillis() - start;
    }
}
