package com.yupi.yuaiagent.cs.core;

import com.yupi.yuaiagent.cs.CsProperties;
import com.yupi.yuaiagent.cs.agent.AgentRunResult;
import com.yupi.yuaiagent.cs.agent.AgentSkill;
import com.yupi.yuaiagent.cs.agent.SkillDispatcher;
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
import com.yupi.yuaiagent.cs.security.AuditService;
import com.yupi.yuaiagent.cs.security.PromptInjectionGuard;
import com.yupi.yuaiagent.cs.support.CriticalServiceDownException;
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
    private final SemanticResponseCache semanticResponseCache;
    private final IntentRouter intentRouter;
    private final CsRagService ragService;
    private final SkillDispatcher skillDispatcher;
    private final HumanTransferService humanTransferService;
    private final FallbackService fallbackService;
    private final MetricsCollector metricsCollector;
    private final CsProperties properties;
    private final PromptInjectionGuard promptInjectionGuard;
    private final AuditService auditService;

    public CsChatOrchestrator(SessionManager sessionManager, ResponseCache responseCache,
                              SemanticResponseCache semanticResponseCache,
                              IntentRouter intentRouter, CsRagService ragService,
                              SkillDispatcher skillDispatcher, HumanTransferService humanTransferService,
                              FallbackService fallbackService, MetricsCollector metricsCollector,
                              CsProperties properties, PromptInjectionGuard promptInjectionGuard,
                              AuditService auditService) {
        this.sessionManager = sessionManager;
        this.responseCache = responseCache;
        this.semanticResponseCache = semanticResponseCache;
        this.intentRouter = intentRouter;
        this.ragService = ragService;
        this.skillDispatcher = skillDispatcher;
        this.humanTransferService = humanTransferService;
        this.fallbackService = fallbackService;
        this.metricsCollector = metricsCollector;
        this.properties = properties;
        this.promptInjectionGuard = promptInjectionGuard;
        this.auditService = auditService;
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

        // 先取「本轮之前」的历史快照，用于查询重写与上下文传递，再把当前消息写入会话
        List<Message> historyBefore = sessionManager.loadTrimmedHistory(session.getSessionId());
        sessionManager.appendMessages(session.getSessionId(), List.of(new UserMessage(message)));

        try {
            // 0. Prompt 注入与危险指令防护：不信任用户输入，命中即拒绝且不转人工（避免被刷人工通道）
            Optional<PromptInjectionGuard.Rule> injection = promptInjectionGuard.detect(message);
            if (injection.isPresent()) {
                auditService.recordSecurity(session.getSessionId(), session.getUserId(),
                        injection.get().code(), message, true);
                metricsCollector.increment("prompt_injection_blocked");
                String answer = "抱歉，您的消息包含不支持的指令，我无法执行。请描述具体业务问题，例如订单、退款或物流。";
                sessionManager.appendMessages(session.getSessionId(), List.of(new AssistantMessage(answer)));
                CsChatResponse response = CsChatResponse.builder()
                        .chatId(session.getSessionId()).route(RouteType.CHAT).answer(answer)
                        .traces(new ArrayList<>(List.of("安全策略拦截：" + injection.get().code())))
                        .costMillis(System.currentTimeMillis() - start).build();
                listener.onMessage(answer);
                listener.onDone(response);
                return response;
            }

            // 1. 缓存短路：先精确命中 FAQ，再走向量语义缓存（相似问法也能命中）
            Optional<String> cached = responseCache.get(message);
            boolean semanticHit = false;
            if (cached.isEmpty()) {
                cached = semanticResponseCache.get(message);
                semanticHit = cached.isPresent();
            }
            if (cached.isPresent()) {
                metricsCollector.recordRoute(RouteType.CHAT.name());
                metricsCollector.increment("cache_hit");
                boolean fromSemantic = semanticHit;
                sessionManager.appendMessages(session.getSessionId(), List.of(new AssistantMessage(cached.get())));
                CsChatResponse response = CsChatResponse.builder()
                        .chatId(session.getSessionId()).route(RouteType.CHAT).answer(cached.get())
                        .confidence(1.0).cacheHit(true)
                        .traces(new ArrayList<>(List.of(fromSemantic ? "语义缓存命中" : "FAQ 精确命中")))
                        .costMillis(System.currentTimeMillis() - start).build();
                listener.onMessage(cached.get());
                listener.onDone(response);
                return response;
            }
            metricsCollector.increment("cache_miss");

            // 2. 重复提问检测：先询问是否需要转人工，而不是直接降级
            CsChatResponse offer = humanOfferGate(message, session, listener, start);
            if (offer != null) {
                sessionManager.appendMessages(session.getSessionId(), List.of(new AssistantMessage(offer.getAnswer())));
                listener.onDone(offer);
                return offer;
            }

            // 3. 路由（有未完成的 Agent 任务时锁定 AGENT 路径，避免多轮确认被重新分流）
            listener.onStage("正在理解您的问题…");
            RouteDecision decision;
            if (session.getAgentState() != null && !session.getAgentState().isBlank()) {
                decision = RouteDecision.of(RouteType.AGENT, 0.95, "继续未完成的售后任务");
                decision.setCategory("refund");
            } else {
                decision = intentRouter.route(message, session, historyBefore);
                decision = applyModeOverride(decision, request.getMode());
            }
            metricsCollector.recordRoute(decision.getRoute().name());
            listener.onRoute(decision);

            // 4. 分发执行
            CsChatResponse response = switch (decision.getRoute()) {
                case CHAT -> handleChat(message, decision, session, historyBefore, listener, start);
                case AGENT -> handleAgent(message, decision, session, historyBefore, listener, request.getToken(), start);
                case HUMAN -> handleHuman(decision, session, listener, start);
            };

            metricsCollector.recordPath(decision.getRoute().name(), response.getCostMillis());
            sessionManager.appendMessages(session.getSessionId(), List.of(new AssistantMessage(response.getAnswer())));
            listener.onDone(response);
            return response;
        } catch (CriticalServiceDownException e) {
            // 关键服务不可用：不重试，立即转人工，避免模型盲答产生幻觉
            log.error("[CS] 关键服务熔断 sessionId={}, service={}, error={}",
                    session.getSessionId(), e.getService(), e.getMessage());
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
                                      List<Message> history, CsEventListener listener, long start) {
        listener.onStage("正在检索知识库…");
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
        // 全量暂存：精确缓存 + 语义缓存双写；命中达阈值后自动晋升为长效缓存
        responseCache.put(message, ragAnswer.answer());
        semanticResponseCache.put(message, ragAnswer.answer(), decision.getCategory());
        return CsChatResponse.builder()
                .chatId(session.getSessionId()).route(RouteType.CHAT).answer(ragAnswer.answer())
                .confidence(ragAnswer.confidence()).traces(ragAnswer.sources())
                .costMillis(System.currentTimeMillis() - start).build();
    }

    private CsChatResponse handleAgent(String message, RouteDecision decision, SessionContext session,
                                       List<Message> history, CsEventListener listener, String token, long start) {
        listener.onStage("正在执行任务…");
        // 技能分发：按路由识别出的 skillId 找到具体 Agent 实现
        AgentSkill skill = skillDispatcher.dispatch(decision.getSkillId());
        AgentRunResult result = skill.run(message, session, decision.getCategory(), token, history);

        if (result.needHuman()) {
            TransferReason reason = switch (String.valueOf(result.fallbackReason())) {
                case "LOOP_DETECTED" -> TransferReason.LOOP_DETECTED;
                case "AI_ESCALATE" -> TransferReason.AI_DECISION;
                default -> TransferReason.AGENT_FAILURE;
            };
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
        TransferRecord record = humanTransferService.transferToHuman(session, reason, decision.getEmotionScore());
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

    /**
     * 重复提问检测：同一问题多次出现时，先询问是否需要转人工，而不是直接降级。
     * 同时消费上一轮的转人工询问结果。
     *
     * @return 已经给出回复时返回响应，否则返回 null 继续正常流程
     */
    private CsChatResponse humanOfferGate(String message, SessionContext session,
                                          CsEventListener listener, long start) {
        // 用户对上一轮「是否需要转人工」的回应
        if (session.isPendingHumanOffer()) {
            session.setPendingHumanOffer(false);
            if (isAffirmative(message)) {
                RouteDecision decision = RouteDecision.of(RouteType.HUMAN, 1.0, "用户确认转人工");
                return handleHuman(decision, session, listener, start);
            }
        }

        String key = responseCache.normalize(message);
        if (key.length() < 2) {
            return null;
        }
        if (key.equals(session.getLastQuestionKey())) {
            int count = session.getRepeatQuestionCount() + 1;
            session.setRepeatQuestionCount(count);
            int required = properties.getHuman().getRepeatQuestionStrikes();
            if (count >= required) {
                session.setPendingHumanOffer(true);
                session.setRepeatQuestionCount(0);
                metricsCollector.increment("human_offer");
                String answer = "您已经连续 " + required + " 次询问同一个问题，可能是我之前的回答没有解决您的问题。"
                        + "需要为您转接人工客服吗？回复「转人工」即可；如果希望我继续处理，请补充更多细节。";
                listener.onMessage(answer);
                return CsChatResponse.builder()
                        .chatId(session.getSessionId()).route(RouteType.CHAT).answer(answer)
                        .confidence(1.0)
                        .traces(new ArrayList<>(List.of("重复问题，询问是否转人工")))
                        .costMillis(System.currentTimeMillis() - start).build();
            }
        } else {
            session.setLastQuestionKey(key);
            session.setRepeatQuestionCount(1);
        }
        return null;
    }

    private boolean isAffirmative(String message) {
        if (message == null) {
            return false;
        }
        String text = message.trim();
        if (text.contains("不用") || text.contains("不需要") || text.contains("算了") || text.contains("不了")) {
            return false;
        }
        if (text.contains("转人工") || text.contains("人工")) {
            return true;
        }
        return text.equals("是") || text.equals("好") || text.equals("好的") || text.equals("行")
                || text.contains("需要") || text.contains("可以") || text.contains("麻烦");
    }

    /**
     * 前端双入口：RAG 入口强制知识库，Agent 入口强制任务执行；转人工意图始终优先
     */
    private RouteDecision applyModeOverride(RouteDecision decision, String mode) {
        if (mode == null || "AUTO".equalsIgnoreCase(mode) || decision.getRoute() == RouteType.HUMAN) {
            return decision;
        }
        if ("RAG".equalsIgnoreCase(mode)) {
            decision.setRoute(RouteType.CHAT);
            if (decision.getSkillId() != null) {
                decision.setReason("知识库问答入口：" + decision.getReason());
            }
            return decision;
        }
        if ("AGENT".equalsIgnoreCase(mode)) {
            decision.setRoute(RouteType.AGENT);
            if (decision.getSkillId() == null) {
                decision.setSkillId("ORDER_SKILL");
            }
            if (decision.getCategory() == null) {
                decision.setCategory("order");
            }
            decision.setReason("智能体任务入口：" + (decision.getReason() == null ? "" : decision.getReason()));
        }
        return decision;
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
