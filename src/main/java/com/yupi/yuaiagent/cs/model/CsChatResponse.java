package com.yupi.yuaiagent.cs.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

/**
 * 客服聊天响应
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CsChatResponse {

    private String chatId;

    private RouteType route;

    private String answer;

    /** 路由/检索置信度 */
    private double confidence;

    private boolean needHuman;

    private String transferId;

    private String transferAgent;

    /** 命中的知识库分类或 Agent 执行轨迹 */
    @Builder.Default
    private List<String> traces = new ArrayList<>();

    /** 是否命中 FAQ 缓存 */
    private boolean cacheHit;

    private long costMillis;
}
