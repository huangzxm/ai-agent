package com.yupi.yuaiagent.cs.agent;

import cn.hutool.json.JSONUtil;

import java.util.HashMap;
import java.util.Map;

/**
 * 死循环检测：同一工具 + 同一参数重复调用超过阈值即终止，避免 Agent 原地打转
 */
public class LoopDetector {

    private final int repeatLimit;

    private final Map<String, Integer> callCounts = new HashMap<>();

    public LoopDetector(int repeatLimit) {
        this.repeatLimit = Math.max(1, repeatLimit);
    }

    /**
     * @return true 表示检测到死循环
     */
    public boolean record(String toolName, Map<String, Object> arguments) {
        String key = toolName + "#" + JSONUtil.toJsonStr(arguments == null ? Map.of() : arguments);
        int count = callCounts.merge(key, 1, Integer::sum);
        return count >= repeatLimit;
    }

    public Map<String, Integer> snapshot() {
        return Map.copyOf(callCounts);
    }
}
