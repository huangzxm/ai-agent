package com.yupi.yuaiagent.cs.api;

import com.yupi.yuaiagent.cs.human.AgentDispatcher;
import com.yupi.yuaiagent.cs.human.HumanTransferService;
import com.yupi.yuaiagent.cs.model.TransferRecord;
import com.yupi.yuaiagent.cs.agent.SkillDispatcher;
import com.yupi.yuaiagent.cs.security.AuditService;
import com.yupi.yuaiagent.cs.support.MetricsCollector;
import com.yupi.yuaiagent.cs.support.ServiceCircuitBreaker;
import com.yupi.yuaiagent.cs.tool.ToolRegistry;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * 客服运营后台：指标、转人工队列、审计、工具热插拔
 */
@RestController
@RequestMapping("/cs/admin")
public class CsAdminController {

    private final MetricsCollector metricsCollector;
    private final HumanTransferService humanTransferService;
    private final AgentDispatcher agentDispatcher;
    private final AuditService auditService;
    private final ToolRegistry toolRegistry;
    private final ServiceCircuitBreaker circuitBreaker;
    private final SkillDispatcher skillDispatcher;

    public CsAdminController(MetricsCollector metricsCollector, HumanTransferService humanTransferService,
                             AgentDispatcher agentDispatcher, AuditService auditService,
                             ToolRegistry toolRegistry, ServiceCircuitBreaker circuitBreaker,
                             SkillDispatcher skillDispatcher) {
        this.metricsCollector = metricsCollector;
        this.humanTransferService = humanTransferService;
        this.agentDispatcher = agentDispatcher;
        this.auditService = auditService;
        this.toolRegistry = toolRegistry;
        this.circuitBreaker = circuitBreaker;
        this.skillDispatcher = skillDispatcher;
    }

    @GetMapping("/circuit-breaker")
    public Map<String, Object> circuitBreaker() {
        return circuitBreaker.status();
    }

    @GetMapping("/skills")
    public Map<String, Object> skills() {
        return Map.of("skills", skillDispatcher.skillIds());
    }

    @GetMapping("/metrics")
    public Map<String, Object> metrics() {
        return metricsCollector.snapshot();
    }

    @GetMapping("/transfers")
    public List<TransferRecord> transfers() {
        return humanTransferService.list();
    }

    @PostMapping("/transfers/{transferId}/accept")
    public TransferRecord accept(@PathVariable String transferId) {
        return humanTransferService.accept(transferId);
    }

    @PostMapping("/transfers/{transferId}/finish")
    public TransferRecord finish(@PathVariable String transferId) {
        return humanTransferService.finish(transferId);
    }

    @GetMapping("/seats")
    public Map<String, Object> seats() {
        return Map.of("seats", agentDispatcher.status(), "queueDepth", agentDispatcher.queueDepth());
    }

    @GetMapping("/audit")
    public List<AuditService.AuditEntry> audit(@RequestParam(defaultValue = "50") int limit) {
        return auditService.recent(limit);
    }

    @GetMapping("/audit/denied")
    public List<AuditService.AuditEntry> denied(@RequestParam(defaultValue = "50") int limit) {
        return auditService.denied(limit);
    }

    @GetMapping("/tools")
    public Map<String, String> tools() {
        return toolRegistry.sources();
    }

    /**
     * MCP 工具热插拔：MCP 服务上下线后调用即可刷新，无需重启
     */
    @PostMapping("/tools/refresh")
    public Map<String, String> refreshTools() {
        toolRegistry.refreshMcpTools();
        return toolRegistry.sources();
    }
}
