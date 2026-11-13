package com.yupi.yuaiagent.cs.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

/**
 * 转人工记录（本地模拟坐席）
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TransferRecord {

    public enum Status { PENDING, IN_SERVICE, DONE }

    private String transferId;

    private String sessionId;

    private String userId;

    private String reason;

    private String category;

    private String assignedAgent;

    @Builder.Default
    private Status status = Status.PENDING;

    /** 会话上下文快照，坐席接单时可见 */
    @Builder.Default
    private List<String> contextSnapshot = new ArrayList<>();

    private long createdAt = System.currentTimeMillis();

    private long assignedAt;

    private long finishedAt;
}
