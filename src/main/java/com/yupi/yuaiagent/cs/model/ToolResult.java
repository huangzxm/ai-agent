package com.yupi.yuaiagent.cs.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 工具执行结果（网关统一标准化）
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ToolResult {

    private boolean success;

    private String toolName;

    /** 给 LLM 阅读的结果文本 */
    private String content;

    /** 错误码：AUTH_DENIED / PERMISSION_DENIED / RESOURCE_DENIED / TOOL_ERROR / NOT_FOUND */
    private String errorCode;

    private long costMillis;

    public static ToolResult ok(String toolName, String content, long costMillis) {
        return ToolResult.builder().success(true).toolName(toolName)
                .content(content).costMillis(costMillis).build();
    }

    public static ToolResult fail(String toolName, String errorCode, String content, long costMillis) {
        return ToolResult.builder().success(false).toolName(toolName)
                .errorCode(errorCode).content(content).costMillis(costMillis).build();
    }
}
