package com.yupi.yuaiagent.cs.model;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * 会话上下文：会话生命周期内需要跨请求保留的状态
 */
@Data
public class SessionContext {

    private String sessionId;

    private String userId;

    private String channel = "web";

    private SessionState state = SessionState.ACTIVE;

    /** 最近一次路由结果，便于人工坐席了解上下文 */
    private RouteType lastRoute;

    /** 最近一次命中的业务分类（物流/退款/发票等） */
    private String lastCategory;

    /** 当前关联的转人工记录 */
    private String activeTransferId;

    /** 会话内已执行过的工具，转人工时打包给坐席 */
    private List<String> executedTools = new ArrayList<>();

    /** Agent 任务状态机状态（跨轮恢复，支持多轮确认） */
    private String agentState;

    /** Agent 已收集的参数（订单号等） */
    private java.util.Map<String, Object> agentParams = new java.util.HashMap<>();

    /** 连续情绪激动次数，达到阈值才转人工 */
    private int angerCount;

    /** AI 主动申请转人工次数，需多次申请才真正生效 */
    private int escalateCount;

    public int incrementAnger() {
        return ++angerCount;
    }

    public void decayAnger() {
        if (angerCount > 0) {
            angerCount--;
        }
    }

    public int incrementEscalate() {
        return ++escalateCount;
    }

    public void resetEscalate() {
        escalateCount = 0;
    }

    private int messageCount;

    private long createdAt = System.currentTimeMillis();

    private long lastActiveAt = System.currentTimeMillis();

    public void touch() {
        this.lastActiveAt = System.currentTimeMillis();
    }
}
