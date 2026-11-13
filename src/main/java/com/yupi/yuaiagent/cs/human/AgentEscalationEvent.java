package com.yupi.yuaiagent.cs.human;

import org.springframework.context.ApplicationEvent;

/**
 * Agent 降级事件：任何路径转人工时发布，全局监听器负责通知人工客服
 */
public class AgentEscalationEvent extends ApplicationEvent {

    private final EscalationContext context;

    public AgentEscalationEvent(Object source, EscalationContext context) {
        super(source);
        this.context = context;
    }

    public String getSessionId() {
        return context.getSessionId();
    }

    public String getBusinessType() {
        return context.getBusinessType();
    }

    public String getReason() {
        return context.getReason();
    }

    public String getTraceId() {
        return context.getTraceId();
    }

    public EscalationContext getContext() {
        return context;
    }
}
