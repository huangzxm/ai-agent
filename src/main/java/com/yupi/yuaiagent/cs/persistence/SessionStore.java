package com.yupi.yuaiagent.cs.persistence;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yupi.yuaiagent.cs.model.SessionContext;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

/**
 * 会话持久化：将 SessionContext 序列化存入 cs_session。
 * 数据库不可用时静默跳过，会话仍以内存为主。
 */
@Component
@Slf4j
public class SessionStore {

    private final ObjectProvider<JdbcTemplate> jdbcTemplateProvider;
    private final ObjectMapper objectMapper;

    public SessionStore(ObjectProvider<JdbcTemplate> jdbcTemplateProvider, ObjectMapper objectMapper) {
        this.jdbcTemplateProvider = jdbcTemplateProvider;
        this.objectMapper = objectMapper;
    }

    public boolean isAvailable() {
        return jdbcTemplateProvider.getIfAvailable() != null;
    }

    public Optional<SessionContext> load(String sessionId) {
        if (!isAvailable()) {
            return Optional.empty();
        }
        try {
            List<String> rows = jdbc().query(
                    "SELECT context_json FROM cs_session WHERE session_id = ?",
                    (rs, rowNum) -> rs.getString("context_json"),
                    sessionId);
            if (rows.isEmpty() || rows.get(0) == null) {
                return Optional.empty();
            }
            return Optional.of(objectMapper.readValue(rows.get(0), SessionContext.class));
        } catch (Exception e) {
            log.warn("[CS] 会话读取失败 sessionId={} error={}", sessionId, e.getMessage());
            return Optional.empty();
        }
    }

    public void save(SessionContext context) {
        if (!isAvailable() || context == null) {
            return;
        }
        try {
            String json = objectMapper.writeValueAsString(context);
            int updated = jdbc().update(
                    "UPDATE cs_session SET user_id = ?, state = ?, context_json = ?, last_active_at = ? "
                            + "WHERE session_id = ?",
                    context.getUserId(), context.getState().name(), json,
                    context.getLastActiveAt(), context.getSessionId());
            if (updated == 0) {
                jdbc().update("INSERT INTO cs_session (session_id, user_id, state, context_json, last_active_at) "
                                + "VALUES (?, ?, ?, ?, ?)",
                        context.getSessionId(), context.getUserId(), context.getState().name(),
                        json, context.getLastActiveAt());
            }
        } catch (Exception e) {
            log.warn("[CS] 会话保存失败 sessionId={} error={}", context.getSessionId(), e.getMessage());
        }
    }

    private JdbcTemplate jdbc() {
        return jdbcTemplateProvider.getObject();
    }
}
