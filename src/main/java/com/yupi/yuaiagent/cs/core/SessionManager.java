package com.yupi.yuaiagent.cs.core;

import com.yupi.yuaiagent.chatmemory.FileBasedChatMemory;
import com.yupi.yuaiagent.cs.CsProperties;
import com.yupi.yuaiagent.cs.model.SessionContext;
import com.yupi.yuaiagent.cs.model.SessionState;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.messages.Message;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * 会话管理：加载/新建会话、上下文动态裁剪、会话状态流转
 */
@Component
@Slf4j
public class SessionManager {

    /** 会话空闲超过 2 小时自动回收（文件历史仍保留） */
    private static final long IDLE_EVICT_MILLIS = 2 * 60 * 60 * 1000L;

    private final Map<String, SessionContext> sessions = new ConcurrentHashMap<>();

    private final ChatMemory chatMemory;

    private final CsProperties properties;

    private final ScheduledExecutorService cleaner = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "cs-session-cleaner");
        t.setDaemon(true);
        return t;
    });

    public SessionManager(CsProperties properties) {
        this.properties = properties;
        String dir = System.getProperty("user.dir") + "/tmp/cs-chat-memory";
        this.chatMemory = new FileBasedChatMemory(dir);
        cleaner.scheduleWithFixedDelay(this::evictIdleSessions, 30, 30, TimeUnit.MINUTES);
    }

    /**
     * 加载已有会话或创建新会话
     */
    public SessionContext getOrCreate(String sessionId, String userId, String channel) {
        return sessions.compute(sessionId, (id, existing) -> {
            if (existing == null) {
                SessionContext created = new SessionContext();
                created.setSessionId(id);
                created.setUserId(userId);
                created.setChannel(channel);
                log.info("[CS] 新建会话 sessionId={}, userId={}", id, userId);
                return created;
            }
            existing.touch();
            return existing;
        });
    }

    public SessionContext get(String sessionId) {
        return sessions.get(sessionId);
    }

    /**
     * 追加历史消息（用户/助手各一条）
     */
    public void appendMessages(String sessionId, List<Message> messages) {
        chatMemory.add(sessionId, messages);
        SessionContext ctx = sessions.get(sessionId);
        if (ctx != null) {
            ctx.setMessageCount(ctx.getMessageCount() + messages.size());
            ctx.touch();
        }
    }

    /**
     * 上下文动态裁剪：只取最近 N 条，超出部分折叠为一句摘要，避免历史无限膨胀
     */
    public List<Message> loadTrimmedHistory(String sessionId) {
        int historySize = properties.getRag().getHistorySize();
        List<Message> recent = chatMemory.get(sessionId, historySize);
        return new ArrayList<>(recent);
    }

    /** 估算保留上下文占用的 token（粗估：中文字符≈1 token，英文≈0.75） */
    public int estimateTokens(List<Message> messages) {
        int chars = messages.stream().mapToInt(m -> m.getText() == null ? 0 : m.getText().length()).sum();
        return (int) (chars * 0.8);
    }

    public List<Message> getRecentTexts(String sessionId, int n) {
        return chatMemory.get(sessionId, n);
    }

    public void updateState(String sessionId, SessionState state) {
        SessionContext ctx = sessions.get(sessionId);
        if (ctx != null) {
            ctx.setState(state);
            ctx.touch();
        }
    }

    public void close(String sessionId) {
        updateState(sessionId, SessionState.CLOSED);
    }

    private void evictIdleSessions() {
        long now = System.currentTimeMillis();
        sessions.entrySet().removeIf(e -> {
            boolean idle = now - e.getValue().getLastActiveAt() > IDLE_EVICT_MILLIS;
            if (idle) {
                log.info("[CS] 回收空闲会话 sessionId={}", e.getKey());
            }
            return idle;
        });
    }

    @PreDestroy
    public void destroy() {
        cleaner.shutdownNow();
    }
}
