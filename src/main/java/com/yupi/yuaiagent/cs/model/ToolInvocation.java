package com.yupi.yuaiagent.cs.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.HashMap;
import java.util.Map;

/**
 * 一次工具调用请求（进入网关前的标准化结构）
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ToolInvocation {

    private String toolName;

    @Builder.Default
    private Map<String, Object> arguments = new HashMap<>();

    private String token;

    private String sessionId;

    public Object arg(String key) {
        return arguments == null ? null : arguments.get(key);
    }
}
