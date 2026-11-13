package com.yupi.yuaiagent.cs.human;

import com.yupi.yuaiagent.cs.core.SessionManager;
import com.yupi.yuaiagent.cs.model.SessionContext;
import org.springframework.ai.chat.messages.Message;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * 上下文打包：把最近对话、路由信息、已执行工具整理成坐席可读的快照
 */
@Component
public class ContextPackager {

    private static final int SNAPSHOT_SIZE = 6;

    private final SessionManager sessionManager;

    public ContextPackager(SessionManager sessionManager) {
        this.sessionManager = sessionManager;
    }

    public List<String> packageContext(SessionContext context) {
        List<String> snapshot = new ArrayList<>();
        snapshot.add("会话ID：" + context.getSessionId());
        snapshot.add("用户ID：" + context.getUserId());
        snapshot.add("最近路由：" + context.getLastRoute());
        if (context.getLastCategory() != null) {
            snapshot.add("业务分类：" + context.getLastCategory());
        }
        if (!context.getExecutedTools().isEmpty()) {
            snapshot.add("已执行工具：" + String.join(", ", context.getExecutedTools()));
        }
        List<Message> history = sessionManager.getRecentTexts(context.getSessionId(), SNAPSHOT_SIZE);
        for (Message message : history) {
            String text = message.getText();
            if (text != null && !text.isBlank()) {
                snapshot.add(message.getMessageType().name() + "：" + text);
            }
        }
        return snapshot;
    }
}
