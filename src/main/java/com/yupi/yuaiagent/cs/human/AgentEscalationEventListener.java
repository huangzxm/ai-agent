package com.yupi.yuaiagent.cs.human;

import com.yupi.yuaiagent.cs.support.MetricsCollector;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * 全局降级监听：收到事件立即通知人工客服（同步执行，保证"立刻"）
 */
@Component
@Slf4j
public class AgentEscalationEventListener {

    private final HumanAgentNotifier notifier;
    private final MetricsCollector metricsCollector;

    public AgentEscalationEventListener(HumanAgentNotifier notifier, MetricsCollector metricsCollector) {
        this.notifier = notifier;
        this.metricsCollector = metricsCollector;
    }

    @EventListener
    public void onEscalation(AgentEscalationEvent event) {
        log.warn("[CS-降级事件] sessionId={}, businessType={}, reason={}, traceId={}",
                event.getSessionId(), event.getBusinessType(), event.getReason(), event.getTraceId());
        metricsCollector.increment("escalation_event");
        notifier.notifyHumanAgent(event.getContext());
    }
}
