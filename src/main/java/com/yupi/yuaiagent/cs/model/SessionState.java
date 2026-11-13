package com.yupi.yuaiagent.cs.model;

/**
 * 会话状态
 */
public enum SessionState {
    /** 机器人在服务 */
    ACTIVE,
    /** 已转人工，排队等待坐席 */
    WAITING_HUMAN,
    /** 坐席服务中 */
    HUMAN_SERVING,
    /** 会话结束 */
    CLOSED
}
