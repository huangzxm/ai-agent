package com.yupi.yuaiagent.cs.human;

import com.yupi.yuaiagent.cs.core.SessionManager;
import com.yupi.yuaiagent.cs.model.RouteType;
import com.yupi.yuaiagent.cs.model.SessionContext;
import org.slf4j.MDC;
import org.springframework.ai.chat.messages.Message;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * 上下文打包：Chat 与 Agent 统一构建 EscalationContext
 */
@Component
public class ContextPackager {

    private static final int SNAPSHOT_SIZE = 6;

    private static final Pattern ENTITY_PATTERN = Pattern.compile(
            "(?<![0-9A-Za-z])([A-Za-z]{2}-\\d{4,}|\\d{4,})(?![0-9A-Za-z])");

    private final SessionManager sessionManager;

    public ContextPackager(SessionManager sessionManager) {
        this.sessionManager = sessionManager;
    }

    public EscalationContext packageContext(SessionContext session, String reason,
                                            double sentiment, List<String> triedSteps) {
        List<Message> history = sessionManager.getRecentTexts(session.getSessionId(), SNAPSHOT_SIZE);
        List<String> chatHistory = history.stream()
                .filter(m -> m.getText() != null && !m.getText().isBlank())
                .map(m -> m.getMessageType().name() + "：" + m.getText())
                .collect(Collectors.toList());

        String originalRequest = history.stream()
                .filter(m -> m.getText() != null && !m.getText().isBlank())
                .reduce((first, second) -> second)
                .map(Message::getText)
                .orElse(null);

        String businessType = buildBusinessType(session);
        return EscalationContext.builder()
                .sessionId(session.getSessionId())
                .userId(session.getUserId())
                .originalRequest(originalRequest)
                .chatHistory(chatHistory)
                .businessType(businessType)
                .reason(reason)
                .triedSteps(triedSteps == null ? new ArrayList<>() : new ArrayList<>(triedSteps))
                .userSentiment(sentiment)
                .entities(extractEntities(originalRequest))
                .traceId(MDC.get("traceId"))
                .createdAt(System.currentTimeMillis())
                .build();
    }

    private String buildBusinessType(SessionContext session) {
        String prefix = session.getLastRoute() == RouteType.AGENT ? "agent" : "chat";
        String category = session.getLastCategory() == null ? "general" : session.getLastCategory();
        return prefix + "_" + category;
    }

    /** 抽取订单号/采购单号，坐席可直接定位业务单据 */
    private Map<String, String> extractEntities(String text) {
        Map<String, String> entities = new LinkedHashMap<>();
        if (text == null) {
            return entities;
        }
        Matcher matcher = ENTITY_PATTERN.matcher(text);
        int index = 0;
        while (matcher.find() && index < 3) {
            entities.put("id_" + (++index), matcher.group(1));
        }
        return entities;
    }
}
