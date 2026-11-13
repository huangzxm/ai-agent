package com.yupi.yuaiagent.cs.model;

import lombok.Data;

/**
 * 客服聊天请求
 */
@Data
public class CsChatRequest {

    private String message;

    private String chatId;

    private String userId;

    /** 模拟 token，例如 demo-token-u1 */
    private String token;

    private String channel = "web";

    /** 前端入口模式：AUTO（自动路由）/ RAG（强制知识库）/ AGENT（强制任务执行） */
    private String mode = "AUTO";
}
