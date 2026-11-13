package com.yupi.yuaiagent.cs.human;

/**
 * 转人工原因
 */
public enum TransferReason {
    USER_REQUEST("用户主动要求转人工"),
    EMOTION("用户情绪激动，需要安抚"),
    LOW_CONFIDENCE("知识库置信度不足"),
    AGENT_FAILURE("Agent 任务执行失败"),
    LOOP_DETECTED("Agent 检测到死循环"),
    AI_DECISION("AI 判定需人工介入"),
    SYSTEM_ERROR("系统异常降级");

    private final String description;

    TransferReason(String description) {
        this.description = description;
    }

    public String getDescription() {
        return description;
    }
}
