package com.yupi.yuaiagent.cs.core;

import com.yupi.yuaiagent.cs.agent.AgentRunResult;
import com.yupi.yuaiagent.cs.agent.RefundFsmAgent;
import com.yupi.yuaiagent.cs.human.HumanTransferService;
import com.yupi.yuaiagent.cs.human.TransferReason;
import com.yupi.yuaiagent.cs.model.CsChatRequest;
import com.yupi.yuaiagent.cs.model.CsChatResponse;
import com.yupi.yuaiagent.cs.model.RouteDecision;
import com.yupi.yuaiagent.cs.model.RouteType;
import com.yupi.yuaiagent.cs.model.SessionContext;
import com.yupi.yuaiagent.cs.model.SessionState;
import com.yupi.yuaiagent.cs.model.TransferRecord;
import com.yupi.yuaiagent.cs.rag.CsRagService;
import com.yupi.yuaiagent.cs.rag.RagAnswer;
import com.yupi.yuaiagent.cs.router.IntentRouter;
import com.yupi.yuaiagent.cs.support.FallbackService;
import com.yupi.yuaiagent.cs.support.MetricsCollector;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * 客服编排器：会话 → 缓存 → 路由 → 执行 → 降级，全流程唯一入口
 */
@Component
@Slf4j
public class CsChatOrchestrator {

    private final SessionManager sessionManager;
    private final ResponseCache responseCache;
    private final IntentRouter intentRouter;
    private final CsRagService ragService;
    private final RefundFsmAgent fsmAgent;
    private final HumanTransferService humanTransferService;
    private final FallbackService fallbackService;
    private final MetricsCollector metricsCollector;

    public CsChatOrchestrator(SessionManager sessionManager, ResponseCache responseCache,
                              IntentRouter intentRouter, CsRagService ragService,
                              RefundFsmAgent fsmAgent, HumanTransferService humanTransferService,
                              FallbackService fallbackService, MetricsCollector metricsCollector) {
        this.sessionManager = sessionManager;
        this.responseCache = responseCache;
        this.intentRouter = intentRouter;
        this.ragService = ragService;
        this.fsmAgent = fsmAgent;
        this.humanTransferService = humanTransferService;
        this.fallbackService = fallbackService;
        this.metricsCollector = metricsCollector;
    }

    public CsChatResponse chat(CsChatRequest request, CsEventListener listener) {
        long start = System.currentTimeMillis();
        String message = request.getMessage();
        SessionContext session = sessionManager.getOrCreate(request.getChatId(), request.getUserId(), request.getChannel());

        // 已转人工的会话不再走机器人
        if (session.getState() == SessionState.WAITING_HUMAN || session.getState() == SessionState.HUMAN_SERVING) {
            TransferRecord record = humanTransferService.get(session.getActiveTransferId());
            String answer = record == null
                    ? "已为您转接人工客服，请稍候。"
                    : "您正在人工客服服务中（坐席 " + record.getAssignedAgent() + "），请直接描述问题。";
            return CsChatResponse.builder()
                    .chatId(session.getSessionId()).route(RouteType.HUMAN).answer(answer)
                    .needHuman(true).transferId(session.getActiveTransferId())
                    .transferAgent(record == null ? null : record.getAssignedAgent())
                    .costMillis(System.currentTimeMillis() - start).build();
        }

        // 记录用户消息
        sessionManager.appendMessages(session.getSessionId(), List.of(new UserMessage(message)));

        try {
            // 1. FAQ 缓存短路
            Optional<String> cached = responseCache.get(message);
            if (cached.isPresent()) {
                metricsCollector.recordRoute(RouteType.CHAT.name());
                metricsCollector.increment("cache_hit");
                sessionManager.appendMessages(session.getSessionId(), List.of(new AssistantMessage(cached.get())));
                CsChatResponse response = CsChatResponse.builder()
                        .chatId(session.getSessionId()).route(RouteType.CHAT).answer(cached.get())
                        .confidence(1.0).cacheHit(true)
                        .costMillis(System.currentTimeMillis() - start).build();
                listener.onMessage(cached.get());
                listener.onDone(response);
                return response;
            }
            metricsCollector.increment("cache_miss");

            // 2. 路由（有未完成的 Agent 任务时锁定 AGENT 路径，避免多轮确认被重新分流）
            listener.onStage("正在理解您的问题…");
            RouteDecision decision;
            if (session.getAgentState() != null && !session.getAgentState().isBlank()) {
                decision = RouteDecision.of(RouteType.AGENT, 0.95, "继续未完成的售后任务");
                decision.setCategory("refund");
            } else {
                decision = intentRouter.route(message, session);
            }
            metricsCollector.recordRoute(decision.getRoute().name());
            listener.onRoute(decision);

            // 3. 分发执行
            CsChatResponse response = switch (decision.getRoute()) {
                case CHAT -> handleChat(message, decision, session, listener, start);
                case AGENT -> handleAgent(message, decision, session, listener, request.getToken(), start);
                case HUMAN -> handleHuman(decision, session, listener, start);
            };

            metricsCollector.recordPath(decision.getRoute().name(), response.getCostMillis());
            sessionManager.appendMessages(session.getSessionId(), List.of(new AssistantMessage(response.getAnswer())));
            listener.onDone(response);
            return response;
        } catch (Exception e) {
            log.error("[CS] 处理异常 sessionId={}", session.getSessionId(), e);
            FallbackService.FallbackOutcome outcome =
                    fallbackService.fallback(session, e, TransferReason.SYSTEM_ERROR);
            CsChatResponse response = CsChatResponse.builder()
                    .chatId(session.getSessionId()).route(RouteType.HUMAN).answer(outcome.answer())
                    .needHuman(true).transferId(outcome.record().getTransferId())
                    .transferAgent(outcome.record().getAssignedAgent())
                    .costMillis(System.currentTimeMillis() - start).build();
            listener.onTransfer(outcome.record());
            listener.onError(e);
            listener.onDone(response);
            return response;
        }
    }

    private CsChatResponse handleChat(String message, RouteDecision decision, SessionContext session,
                                      CsEventListener listener, long start) {
        listener.onStage("正在检索知识库…");
        List<Message> history = sessionManager.loadTrimmedHistory(session.getSessionId());
        metricsCollector.recordTokens(sessionManager.estimateTokens(history));
        RagAnswer ragAnswer = ragService.answer(message, decision.getCategory(), history);

        if (ragAnswer.needHuman()) {
            FallbackService.FallbackOutcome outcome = fallbackService.lowConfidence(session,
                    "抱歉，我暂时没有找到准确的答案。");
            listener.onTransfer(outcome.record());
            return CsChatResponse.builder()
                    .chatId(session.getSessionId()).route(RouteType.HUMAN).answer(outcome.answer())
                    .confidence(ragAnswer.confidence()).needHuman(true)
                    .transferId(outcome.record().getTransferId())
                    .transferAgent(outcome.record().getAssignedAgent())
                    .costMillis(System.currentTimeMillis() - start).build();
        }
        listener.onMessage(ragAnswer.answer());
        return CsChatResponse.builder()
                .chatId(session.getSessionId()).route(RouteType.CHAT).answer(ragAnswer.answer())
                .confidence(ragAnswer.confidence()).traces(ragAnswer.sources())
                .costMillis(System.currentTimeMillis() - start).build();
    }

    private CsChatResponse handleAgent(String message, RouteDecision decision, SessionContext session,
                                       CsEventListener listener, String token, long start) {
        listener.onStage("正在执行任务…");
        List<Message> history = sessionManager.loadTrimmedHistory(session.getSessionId());
        AgentRunResult result = fsmAgent.run(message, session, decision.getCategory(), token, history);

        if (result.needHuman()) {
            TransferReason reason = "LOOP_DETECTED".equals(result.fallbackReason())
                    ? TransferReason.LOOP_DETECTED : TransferReason.AGENT_FAILURE;
            FallbackService.FallbackOutcome outcome = fallbackService.fallback(session, null, reason);
            listener.onTransfer(outcome.record());
            return CsChatResponse.builder()
                    .chatId(session.getSessionId()).route(RouteType.HUMAN)
                    .answer(result.answer() + outcome.answer())
                    .needHuman(true).transferId(outcome.record().getTransferId())
                    .transferAgent(outcome.record().getAssignedAgent())
                    .traces(result.traces())
                    .costMillis(System.currentTimeMillis() - start).build();
        }
        listener.onMessage(result.answer());
        return CsChatResponse.builder()
                .chatId(session.getSessionId()).route(RouteType.AGENT).answer(result.answer())
                .confidence(decision.getConfidence()).traces(result.traces())
                .costMillis(System.currentTimeMillis() - start).build();
    }

    private CsChatResponse handleHuman(RouteDecision decision, SessionContext session,
                                       CsEventListener listener, long start) {
        TransferReason reason = resolveReason(decision);
        TransferRecord record = humanTransferService.transferToHuman(session, reason);
        String answer = humanTransferService.queueMessage(record);
        listener.onTransfer(record);
        listener.onMessage(answer);
        return CsChatResponse.builder()
                .chatId(session.getSessionId()).route(RouteType.HUMAN).answer(answer)
                .confidence(decision.getConfidence()).needHuman(true)
                .transferId(record.getTransferId()).transferAgent(record.getAssignedAgent())
                .traces(new ArrayList<>(record.getContextSnapshot()))
                .costMillis(System.currentTimeMillis() - start).build();
    }

    private TransferReason resolveReason(RouteDecision decision) {
        String reason = decision.getReason() == null ? "" : decision.getReason();
        if (reason.contains("情绪")) {
            return TransferReason.EMOTION;
        }
        if (reason.contains("置信度")) {
            return TransferReason.LOW_CONFIDENCE;
        }
        return TransferReason.USER_REQUEST;
    }
}
