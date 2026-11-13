package com.yupi.yuaiagent.cs.support;

import com.yupi.yuaiagent.cs.human.AgentEscalationEvent;
import com.yupi.yuaiagent.cs.human.ContextPackager;
import com.yupi.yuaiagent.cs.human.EscalationContext;
import com.yupi.yuaiagent.cs.model.SessionContext;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

/**
 * Agent 兜底监控切面：任何未被捕获的异常都会发布降级事件通知人工客服，避免"漏网之鱼"。
 */
@Aspect
@Component
@Slf4j
public class AgentMonitorAspect {

    private final ApplicationEventPublisher eventPublisher;
    private final ContextPackager contextPackager;

    public AgentMonitorAspect(ApplicationEventPublisher eventPublisher, ContextPackager contextPackager) {
        this.eventPublisher = eventPublisher;
        this.contextPackager = contextPackager;
    }

    @Around("execution(* com.yupi.yuaiagent.cs.agent..*.run(..))")
    public Object monitorAgentRun(ProceedingJoinPoint joinPoint) throws Throwable {
        try {
            return joinPoint.proceed();
        } catch (Throwable e) {
            log.error("[CS-AOP] Agent 出现未捕获异常，发布降级事件 method={}", joinPoint.getSignature().toShortString(), e);
            SessionContext session = findSession(joinPoint.getArgs());
            if (session != null) {
                EscalationContext context = contextPackager.packageContext(
                        session, "未捕获异常：" + e.getClass().getSimpleName(), 0.0, session.getExecutedTools());
                eventPublisher.publishEvent(new AgentEscalationEvent(this, context));
            }
            throw e;
        }
    }

    private SessionContext findSession(Object[] args) {
        if (args == null) {
            return null;
        }
        for (Object arg : args) {
            if (arg instanceof SessionContext session) {
                return session;
            }
        }
        return null;
    }
}
