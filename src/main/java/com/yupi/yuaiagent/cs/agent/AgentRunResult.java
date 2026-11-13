package com.yupi.yuaiagent.cs.agent;

import java.util.List;

/**
 * Agent 执行结果
 */
public record AgentRunResult(
        String answer,
        AgentFsm.State state,
        List<String> traces,
        boolean needHuman,
        String fallbackReason) {

    public static AgentRunResult needHuman(String answer, AgentFsm.State state, List<String> traces, String reason) {
        return new AgentRunResult(answer, state, traces, true, reason);
    }
}
