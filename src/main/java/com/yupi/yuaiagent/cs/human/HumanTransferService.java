package com.yupi.yuaiagent.cs.human;

import com.yupi.yuaiagent.cs.CsProperties;
import com.yupi.yuaiagent.cs.core.SessionManager;
import com.yupi.yuaiagent.cs.model.SessionContext;
import com.yupi.yuaiagent.cs.model.SessionState;
import com.yupi.yuaiagent.cs.model.TransferRecord;
import com.yupi.yuaiagent.cs.support.MetricsCollector;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 人工交接：时机判定 → 上下文打包 → 坐席分配 → 状态跟踪（本地模拟坐席）
 */
@Component
@Slf4j
public class HumanTransferService {

    private final AgentDispatcher agentDispatcher;
    private final ContextPackager contextPackager;
    private final SessionManager sessionManager;
    private final MetricsCollector metricsCollector;
    private final CsProperties properties;
    private final ApplicationEventPublisher eventPublisher;

    private final Map<String, TransferRecord> records = new ConcurrentHashMap<>();
    private final AtomicInteger sequence = new AtomicInteger();

    public HumanTransferService(AgentDispatcher agentDispatcher, ContextPackager contextPackager,
                                SessionManager sessionManager, MetricsCollector metricsCollector,
                                CsProperties properties, ApplicationEventPublisher eventPublisher) {
        this.agentDispatcher = agentDispatcher;
        this.contextPackager = contextPackager;
        this.sessionManager = sessionManager;
        this.metricsCollector = metricsCollector;
        this.properties = properties;
        this.eventPublisher = eventPublisher;
    }

    public TransferRecord transferToHuman(SessionContext context, TransferReason reason) {
        return transferToHuman(context, reason, 0.0);
    }

    /**
     * 转人工入口：幂等，同一会话未结束的转接单直接复用；成功后发布降级事件通知人工客服
     */
    public TransferRecord transferToHuman(SessionContext context, TransferReason reason, double sentiment) {
        String existingId = context.getActiveTransferId();
        if (existingId != null) {
            TransferRecord existing = records.get(existingId);
            if (existing != null && existing.getStatus() != TransferRecord.Status.DONE) {
                return existing;
            }
        }

        if (records.size() >= properties.getHuman().getQueueLimit()) {
            log.warn("[CS] 人工队列已满，会话 {} 进入排队", context.getSessionId());
        }

        String agentId = agentDispatcher.assign();
        agentDispatcher.occupy(agentId);

        // 统一构建降级上下文（Chat 与 Agent 同一套结构）
        EscalationContext escalation = contextPackager.packageContext(
                context, reason.getDescription(), sentiment, context.getExecutedTools());

        TransferRecord record = TransferRecord.builder()
                .transferId("TR" + System.currentTimeMillis() + sequence.incrementAndGet())
                .sessionId(context.getSessionId())
                .userId(context.getUserId())
                .reason(reason.getDescription())
                .category(context.getLastCategory())
                .assignedAgent(agentId)
                .status(TransferRecord.Status.PENDING)
                .contextSnapshot(escalation.toSnapshot())
                .escalation(escalation)
                .createdAt(System.currentTimeMillis())
                .build();

        records.put(record.getTransferId(), record);
        context.setActiveTransferId(record.getTransferId());
        sessionManager.updateState(context.getSessionId(), SessionState.WAITING_HUMAN);
        metricsCollector.increment("human_transfer");
        log.info("[CS] 转人工成功 sessionId={}, transferId={}, agent={}, reason={}",
                context.getSessionId(), record.getTransferId(), agentId, reason.getDescription());
        // 事件驱动：立刻通知人工客服，业务代码不关心通知实现
        eventPublisher.publishEvent(new AgentEscalationEvent(this, escalation));
        return record;
    }

    /**
     * 模拟坐席接单
     */
    public TransferRecord accept(String transferId) {
        TransferRecord record = records.get(transferId);
        if (record == null) {
            return null;
        }
        record.setStatus(TransferRecord.Status.IN_SERVICE);
        record.setAssignedAt(System.currentTimeMillis());
        SessionContext session = sessionManager.get(record.getSessionId());
        if (session != null) {
            sessionManager.updateState(session.getSessionId(), SessionState.HUMAN_SERVING);
        }
        return record;
    }

    /**
     * 模拟坐席结束会话
     */
    public TransferRecord finish(String transferId) {
        TransferRecord record = records.get(transferId);
        if (record == null) {
            return null;
        }
        record.setStatus(TransferRecord.Status.DONE);
        record.setFinishedAt(System.currentTimeMillis());
        agentDispatcher.release(record.getAssignedAgent());
        SessionContext session = sessionManager.get(record.getSessionId());
        if (session != null) {
            session.setActiveTransferId(null);
            sessionManager.close(session.getSessionId());
        }
        return record;
    }

    public List<TransferRecord> list() {
        return records.values().stream()
                .sorted((a, b) -> Long.compare(b.getCreatedAt(), a.getCreatedAt()))
                .toList();
    }

    public TransferRecord get(String transferId) {
        return records.get(transferId);
    }

    public String queueMessage(TransferRecord record) {
        long waiting = records.values().stream()
                .filter(r -> r.getStatus() == TransferRecord.Status.PENDING)
                .count();
        return "已为您转接人工客服，当前排队第 " + waiting + " 位，坐席 " + record.getAssignedAgent()
                + " 将尽快为您服务（转接单号 " + record.getTransferId() + "）。";
    }
}
