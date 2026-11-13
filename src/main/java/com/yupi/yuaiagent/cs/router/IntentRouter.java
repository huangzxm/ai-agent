package com.yupi.yuaiagent.cs.router;

import com.yupi.yuaiagent.cs.CsProperties;
import com.yupi.yuaiagent.cs.core.SessionManager;
import com.yupi.yuaiagent.cs.model.RouteDecision;
import com.yupi.yuaiagent.cs.model.RouteType;
import com.yupi.yuaiagent.cs.model.SessionContext;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.messages.Message;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * 漏斗式路由：规则层（快、省） → LLM 语义层（准） → 阈值兜底
 */
@Component
@Slf4j
public class IntentRouter {

    private final RuleClassifier ruleClassifier;
    private final LlmClassifier llmClassifier;
    private final SessionManager sessionManager;
    private final CsProperties properties;

    public IntentRouter(RuleClassifier ruleClassifier, LlmClassifier llmClassifier,
                        SessionManager sessionManager, CsProperties properties) {
        this.ruleClassifier = ruleClassifier;
        this.llmClassifier = llmClassifier;
        this.sessionManager = sessionManager;
        this.properties = properties;
    }

    public RouteDecision route(String message, SessionContext session) {
        // 1. 规则层
        Optional<RouteDecision> ruleHit = ruleClassifier.classify(message);
        RouteDecision decision = ruleHit.orElseGet(() -> {
            // 2. LLM 语义层（带最近历史摘要）
            List<Message> history = sessionManager.loadTrimmedHistory(session.getSessionId());
            String summary = history.stream()
                    .map(m -> m.getMessageType().name() + ":" + m.getText())
                    .limit(4)
                    .collect(Collectors.joining(" | "));
            return llmClassifier.classify(message, summary).orElseGet(llmClassifier::fallback);
        });

        // 3. 阈值兜底：低置信或高情绪 → 人工
        if (decision.getRoute() != RouteType.HUMAN) {
            if (decision.getConfidence() < properties.getRoute().getConfidenceThreshold()) {
                decision = RouteDecision.of(RouteType.HUMAN, decision.getConfidence(),
                        "置信度不足(" + decision.getConfidence() + ")，转人工兜底");
            } else if (decision.getEmotionScore() > properties.getRoute().getEmotionThreshold()) {
                decision.setRoute(RouteType.HUMAN);
                decision.setReason("情绪强度过高，转人工安抚");
            }
        }

        session.setLastRoute(decision.getRoute());
        session.setLastCategory(decision.getCategory());
        log.info("[CS] 最终路由 sessionId={}, route={}, confidence={}, reason={}",
                session.getSessionId(), decision.getRoute(), decision.getConfidence(), decision.getReason());
        return decision;
    }
}
