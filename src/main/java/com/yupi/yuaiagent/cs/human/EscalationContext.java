package com.yupi.yuaiagent.cs.human;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 统一降级上下文包：Chat 与 Agent 共用，人工坐席据此从断点继续处理，用户无需重新描述问题。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EscalationContext {

    private String sessionId;

    private String userId;

    /** 用户原始诉求 */
    private String originalRequest;

    /** 最近几轮对话 */
    @Builder.Default
    private List<String> chatHistory = new ArrayList<>();

    /** chat_xxx / agent_xxx，用于技能组路由 */
    private String businessType;

    /** 降级原因：检索异常 / 置信度过低 / 工具调用失败 / 死循环 / AI 主动 escalate */
    private String reason;

    /** Agent 已尝试步骤，Chat 模式为空 */
    @Builder.Default
    private List<String> triedSteps = new ArrayList<>();

    /** 用户情绪强度 0~1 */
    private double userSentiment;

    /** 业务实体：订单号、采购单号等 */
    private Map<String, String> entities;

    /** 链路追踪 ID */
    private String traceId;

    @Builder.Default
    private long createdAt = System.currentTimeMillis();

    /** 转给坐席端的可读快照 */
    public List<String> toSnapshot() {
        List<String> snapshot = new ArrayList<>();
        snapshot.add("业务类型：" + businessType);
        snapshot.add("降级原因：" + reason);
        if (originalRequest != null) {
            snapshot.add("原始诉求：" + originalRequest);
        }
        if (entities != null && !entities.isEmpty()) {
            snapshot.add("业务实体：" + entities);
        }
        if (triedSteps != null && !triedSteps.isEmpty()) {
            snapshot.add("已尝试步骤：" + String.join(" → ", triedSteps));
        }
        snapshot.add("用户情绪强度：" + userSentiment);
        if (traceId != null) {
            snapshot.add("TraceId：" + traceId);
        }
        snapshot.addAll(chatHistory);
        return snapshot;
    }
}
