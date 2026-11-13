package com.yupi.yuaiagent.cs.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.HashMap;
import java.util.Map;

/**
 * 路由决策：由规则层或 LLM 层产出
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class RouteDecision {

    private RouteType route;

    /** 置信度 0~1 */
    private double confidence;

    /** 决策原因（可解释性） */
    private String reason;

    /** 业务分类：logistics / refund / invoice / account / policy */
    private String category;

    /** 情绪强度 0~1，越高越需要人工安抚 */
    private double emotionScore;

    /** 路由抽取出的参数，如 orderId */
    private Map<String, Object> params = new HashMap<>();

    public static RouteDecision of(RouteType route, double confidence, String reason) {
        return new RouteDecision(route, confidence, reason, null, 0.0, new HashMap<>());
    }
}
