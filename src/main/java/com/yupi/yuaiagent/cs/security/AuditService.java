package com.yupi.yuaiagent.cs.security;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Map;

/**
 * 安全审计：记录谁在什么时间对哪个资源做了什么
 */
@Component
@Slf4j
public class AuditService {

    private static final int MAX_RECORDS = 500;

    private final Deque<AuditEntry> entries = new ArrayDeque<>();

    public synchronized void record(String sessionId, String userId, String toolName,
                                    Map<String, Object> arguments, boolean success, String errorCode) {
        AuditEntry entry = new AuditEntry(Instant.now().toString(), sessionId, userId,
                toolName, String.valueOf(arguments), success, errorCode);
        entries.addFirst(entry);
        while (entries.size() > MAX_RECORDS) {
            entries.removeLast();
        }
        if (!success) {
            log.warn("[CS-AUDIT] 拦截/失败 sessionId={} userId={} tool={} args={} errorCode={}",
                    sessionId, userId, toolName, arguments, errorCode);
        } else {
            log.info("[CS-AUDIT] 执行 sessionId={} userId={} tool={} args={}", sessionId, userId, toolName, arguments);
        }
    }

    public synchronized List<AuditEntry> recent(int limit) {
        return entries.stream().limit(Math.max(1, limit)).map(e -> e).toList();
    }

    public synchronized List<AuditEntry> denied(int limit) {
        return entries.stream().filter(e -> !e.success()).limit(Math.max(1, limit)).toList();
    }

    public List<AuditEntry> snapshot() {
        return new ArrayList<>(entries);
    }

    public record AuditEntry(String time, String sessionId, String userId, String toolName,
                             String arguments, boolean success, String errorCode) {
    }
}
