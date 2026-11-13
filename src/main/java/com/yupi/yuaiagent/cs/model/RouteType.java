package com.yupi.yuaiagent.cs.model;

/**
 * 路由决策结果
 */
public enum RouteType {
    /** 知识库问答 */
    CHAT,
    /** 工具调用 / 任务执行 */
    AGENT,
    /** 人工客服 */
    HUMAN
}
