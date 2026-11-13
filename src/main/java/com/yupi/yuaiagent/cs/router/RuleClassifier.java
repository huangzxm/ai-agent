package com.yupi.yuaiagent.cs.router;

import com.yupi.yuaiagent.cs.model.RouteDecision;
import com.yupi.yuaiagent.cs.model.RouteType;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 规则层意图识别：零成本、高精度，命中即短路，未决交给 LLM 层
 */
@Component
public class RuleClassifier {

    private static final List<String> HUMAN_KEYWORDS = List.of(
            "转人工", "人工客服", "真人", "找客服", "找经理", "投诉", "举报", "我要投诉", "人工服务");

    private static final List<String> ANGRY_KEYWORDS = List.of(
            "垃圾", "骗子", "欺诈", "黑心", "滚", "废物", "气死", "太离谱", "什么破", "忍无可忍", "差评");

    /** 明确需要调用业务工具的动作词 → 业务分类 */
    private static final Map<String, String> AGENT_KEYWORDS = Map.of(
            "退款", "refund",
            "退货", "refund",
            "取消订单", "refund",
            "修改地址", "order",
            "改地址", "order",
            "开发票", "invoice",
            "采购单", "erp",
            "PR-", "erp");

    /** 业务分类 → Agent 技能ID */
    private static String skillOf(String category) {
        if (category == null) {
            return null;
        }
        return switch (category) {
            case "refund" -> "REFUND_SKILL";
            case "erp" -> "ERP_SKILL";
            default -> "ORDER_SKILL";
        };
    }

    private static final Pattern ORDER_ID_PATTERN = Pattern.compile("(?<![0-9A-Za-z])([A-Za-z]{0,2}\\d{4,})(?![0-9A-Za-z])");

    private static final Pattern EXCLAMATION_RUN = Pattern.compile("[!！]{3,}");

    /**
     * 规则命中返回决策，未决返回 empty
     */
    public Optional<RouteDecision> classify(String message) {
        if (message == null || message.isBlank()) {
            return Optional.empty();
        }
        String text = message.trim();

        // 1. 显式转人工：最高优先级，直接短路
        for (String keyword : HUMAN_KEYWORDS) {
            if (text.contains(keyword)) {
                RouteDecision decision = RouteDecision.of(RouteType.HUMAN, 0.98, "命中转人工关键词：" + keyword);
                decision.setEmotionScore(0.6);
                return Optional.of(decision);
            }
        }

        // 2. 情绪激动：需要人工安抚
        double emotion = detectEmotion(text);
        if (emotion >= 0.8) {
            RouteDecision decision = RouteDecision.of(RouteType.HUMAN, 0.9, "检测到用户情绪激动");
            decision.setEmotionScore(emotion);
            return Optional.of(decision);
        }

        // 3. 明确业务动作：走 Agent 工具调用
        String upperText = text.toUpperCase();
        for (Map.Entry<String, String> entry : AGENT_KEYWORDS.entrySet()) {
            // 采购单号前缀不区分大小写
            boolean matched = "PR-".equals(entry.getKey())
                    ? upperText.contains("PR-")
                    : text.contains(entry.getKey());
            if (matched) {
                RouteDecision decision = RouteDecision.of(RouteType.AGENT, 0.85, "命中业务动作关键词：" + entry.getKey());
                decision.setCategory(entry.getValue());
                decision.setSkillId(skillOf(entry.getValue()));
                decision.setEmotionScore(emotion);
                Map<String, Object> params = new HashMap<>();
                extractOrderId(text).ifPresent(id -> params.put("orderId", id));
                decision.setParams(params);
                return Optional.of(decision);
            }
        }

        // 4. 查询订单/物流：交给 LLM 细分（可能只是政策咨询）
        return Optional.empty();
    }

    /** 情绪打分：辱骂词、连续感叹号、全大写英文、重复催促 */
    public double detectEmotion(String text) {
        double score = 0.0;
        for (String keyword : ANGRY_KEYWORDS) {
            if (text.contains(keyword)) {
                score += 0.45;
            }
        }
        Matcher matcher = EXCLAMATION_RUN.matcher(text);
        if (matcher.find()) {
            score += 0.25;
        }
        if (text.length() > 4 && text.equals(text.toUpperCase()) && text.matches(".*[A-Z].*")) {
            score += 0.2;
        }
        if (text.contains("到底") || text.contains("还没") || text.contains("第几次")) {
            score += 0.15;
        }
        return Math.min(1.0, score);
    }

    public Optional<String> extractOrderId(String text) {
        Matcher matcher = ORDER_ID_PATTERN.matcher(text);
        if (matcher.find()) {
            return Optional.of(matcher.group(1));
        }
        return Optional.empty();
    }
}
