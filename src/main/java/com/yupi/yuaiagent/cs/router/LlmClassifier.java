package com.yupi.yuaiagent.cs.router;

import com.yupi.yuaiagent.cs.model.RouteDecision;
import com.yupi.yuaiagent.cs.model.RouteType;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * LLM 语义层意图识别：规则未决时才调用，输出结构化 RouteDecision
 */
@Component
@Slf4j
public class LlmClassifier {

    private static final String ROUTER_PROMPT = """
            你是电商客服系统的意图分类器。请把用户问题分到三类之一：
            - CHAT：政策咨询、常见问题、使用帮助等，可以由知识库直接回答，不需要操作系统。
            - AGENT：需要实际执行操作的请求，例如查询具体订单状态、退货退款、修改订单、开发票、创建工单。
            - HUMAN：用户要求转人工、情绪激动、投诉升级，或问题复杂到无法判断。

            输出字段：
            route: CHAT/AGENT/HUMAN 之一
            confidence: 0~1 的置信度
            reason: 中文简述判断依据，不超过30字
            category: logistics/refund/order/invoice/account/policy/erp 之一，无法判断给 null
            skillId: 仅当 route=AGENT 时给出技能ID，可选 REFUND_SKILL（退款退货）、ORDER_SKILL（订单/物流/发票）、ERP_SKILL（采购单进度查询），否则给 null
            emotionScore: 0~1 的用户情绪强度
            params: 从用户输入抽取的参数对象，例如 {"orderId":"1001"}，没有就给空对象

            只输出 JSON，不要输出多余内容。
            """;

    private final ChatClient chatClient;

    public LlmClassifier(ChatModel dashscopeChatModel) {
        this.chatClient = ChatClient.builder(dashscopeChatModel).build();
    }

    public Optional<RouteDecision> classify(String message, String historySummary) {
        try {
            RouteDecision decision = chatClient.prompt()
                    .system(ROUTER_PROMPT)
                    .user(u -> u.text("历史对话：{history}\n用户输入：{message}")
                            .param("history", historySummary == null || historySummary.isBlank() ? "无" : historySummary)
                            .param("message", message))
                    .call()
                    .entity(RouteDecision.class);
            if (decision == null || decision.getRoute() == null) {
                return Optional.empty();
            }
            log.info("[CS] LLM路由结果 route={}, confidence={}, category={}, reason={}",
                    decision.getRoute(), decision.getConfidence(), decision.getCategory(), decision.getReason());
            return Optional.of(decision);
        } catch (Exception e) {
            log.warn("[CS] LLM意图识别失败：{}", e.getMessage());
            return Optional.empty();
        }
    }

    /** 兜底：LLM 不可用时给保守决策，由上层阈值决定是否转人工 */
    public RouteDecision fallback() {
        return RouteDecision.of(RouteType.CHAT, 0.3, "意图识别服务异常，保守走知识库");
    }
}
