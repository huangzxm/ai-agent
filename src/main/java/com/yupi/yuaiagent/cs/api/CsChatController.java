package com.yupi.yuaiagent.cs.api;

import cn.hutool.core.util.StrUtil;
import com.yupi.yuaiagent.cs.core.CsChatOrchestrator;
import com.yupi.yuaiagent.cs.core.CsEventListener;
import com.yupi.yuaiagent.cs.core.SessionManager;
import com.yupi.yuaiagent.cs.model.CsChatRequest;
import com.yupi.yuaiagent.cs.model.CsChatResponse;
import com.yupi.yuaiagent.cs.model.SessionContext;
import com.yupi.yuaiagent.cs.model.TransferRecord;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.messages.Message;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * AI 客服统一入口：SSE 流式 + 同步调用
 */
@RestController
@RequestMapping("/cs")
@Slf4j
public class CsChatController {

    /** 流式分片大小 */
    private static final int CHUNK_SIZE = 24;

    private final CsChatOrchestrator orchestrator;
    private final SessionManager sessionManager;

    public CsChatController(CsChatOrchestrator orchestrator, SessionManager sessionManager) {
        this.orchestrator = orchestrator;
        this.sessionManager = sessionManager;
    }

    /**
     * SSE 流式对话：route → stage → message(分片) → transfer → done
     */
    @GetMapping(value = "/chat/sse", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter chatStream(@RequestParam String message,
                                 @RequestParam(required = false) String chatId,
                                 @RequestParam(required = false) String userId,
                                 @RequestParam(required = false) String token,
                                 @RequestParam(defaultValue = "web") String channel) {
        CsChatRequest request = buildRequest(message, chatId, userId, token, channel);
        SseEmitter emitter = new SseEmitter(180000L);

        // 异步推理，避免阻塞 Web 容器线程
        CompletableFuture.runAsync(() -> {
            try {
                orchestrator.chat(request, new CsEventListener() {
                    @Override
                    public void onRoute(com.yupi.yuaiagent.cs.model.RouteDecision decision) {
                        send(emitter, "route", Map.of(
                                "route", decision.getRoute().name(),
                                "confidence", decision.getConfidence(),
                                "category", decision.getCategory() == null ? "" : decision.getCategory(),
                                "reason", decision.getReason() == null ? "" : decision.getReason()));
                    }

                    @Override
                    public void onStage(String stage) {
                        send(emitter, "stage", Map.of("text", stage));
                    }

                    @Override
                    public void onMessage(String chunk) {
                        streamText(emitter, chunk);
                    }

                    @Override
                    public void onTransfer(TransferRecord record) {
                        send(emitter, "transfer", Map.of(
                                "transferId", record.getTransferId(),
                                "agent", record.getAssignedAgent(),
                                "reason", record.getReason()));
                    }

                    @Override
                    public void onDone(CsChatResponse response) {
                        send(emitter, "done", response);
                        emitter.complete();
                    }

                    @Override
                    public void onError(Throwable throwable) {
                        // 事件名用 fail，避免与 EventSource 的 error 连接事件混淆
                        send(emitter, "fail", Map.of("message", String.valueOf(throwable.getMessage())));
                    }
                });
            } catch (Exception e) {
                log.error("[CS] SSE 处理失败", e);
                send(emitter, "error", Map.of("message", "系统异常，请稍后重试"));
                emitter.complete();
            }
        });

        emitter.onTimeout(emitter::complete);
        return emitter;
    }

    /**
     * 同步对话，便于调试与联调
     */
    @PostMapping("/chat")
    public CsChatResponse chat(@RequestBody CsChatRequest request) {
        request.setChatId(StrUtil.blankToDefault(request.getChatId(), UUID.randomUUID().toString()));
        request.setUserId(StrUtil.blankToDefault(request.getUserId(), "anonymous"));
        return orchestrator.chat(request, CsEventListener.NOOP);
    }

    /**
     * 查询会话历史
     */
    @GetMapping("/chat/history")
    public Map<String, Object> history(@RequestParam String chatId) {
        SessionContext session = sessionManager.get(chatId);
        List<Message> messages = sessionManager.getRecentTexts(chatId, 50);
        List<Map<String, String>> items = new ArrayList<>();
        for (Message message : messages) {
            Map<String, String> item = new LinkedHashMap<>();
            item.put("role", message.getMessageType().name().toLowerCase());
            item.put("content", message.getText());
            items.add(item);
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("chatId", chatId);
        result.put("state", session == null ? "UNKNOWN" : session.getState().name());
        result.put("messages", items);
        return result;
    }

    /**
     * 结束会话
     */
    @PostMapping("/chat/close")
    public Map<String, Object> close(@RequestParam String chatId) {
        sessionManager.close(chatId);
        return Map.of("chatId", chatId, "state", "CLOSED");
    }

    private CsChatRequest buildRequest(String message, String chatId, String userId, String token, String channel) {
        CsChatRequest request = new CsChatRequest();
        request.setMessage(message);
        request.setChatId(StrUtil.blankToDefault(chatId, UUID.randomUUID().toString()));
        request.setUserId(StrUtil.blankToDefault(userId, "anonymous"));
        request.setToken(token);
        request.setChannel(channel);
        return request;
    }

    private void streamText(SseEmitter emitter, String text) {
        if (text == null || text.isEmpty()) {
            return;
        }
        for (int i = 0; i < text.length(); i += CHUNK_SIZE) {
            String chunk = text.substring(i, Math.min(text.length(), i + CHUNK_SIZE));
            if (!send(emitter, "message", Map.of("text", chunk))) {
                return;
            }
            try {
                Thread.sleep(12);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
        }
    }

    private boolean send(SseEmitter emitter, String event, Object data) {
        try {
            emitter.send(SseEmitter.event().name(event).data(data, MediaType.APPLICATION_JSON));
            return true;
        } catch (Exception e) {
            log.debug("[CS] SSE 发送失败（客户端可能已断开）：{}", e.getMessage());
            return false;
        }
    }
}
