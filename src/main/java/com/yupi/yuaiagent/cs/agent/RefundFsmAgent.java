package com.yupi.yuaiagent.cs.agent;

import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.alibaba.cloud.ai.dashscope.chat.DashScopeChatOptions;
import com.yupi.yuaiagent.cs.CsProperties;
import com.yupi.yuaiagent.cs.model.SessionContext;
import com.yupi.yuaiagent.cs.model.ToolInvocation;
import com.yupi.yuaiagent.cs.model.ToolResult;
import com.yupi.yuaiagent.cs.tool.ToolGateway;
import com.yupi.yuaiagent.cs.tool.ToolRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 售后 Agent：FSM 约束 + ReAct 循环 + CoT 动态 Prompt + 死循环检测。
 * 工具只经 ToolGateway 执行，权限与订单归属由代码强制校验。
 */
@Component
@Slf4j
public class RefundFsmAgent implements AgentSkill {

    private static final String SYSTEM_PROMPT = """
            你是电商平台的售后客服 Agent，负责订单查询、物流查询与退款办理。
            你必须严格遵守状态机流转，不允许跳步：
            1. 先核实订单（queryOrder），确认订单存在且状态允许退款；
            2. 核实后向用户列出订单号、商品、金额，明确询问「是否确认退款」；
            3. 只有用户明确表示确认后，才允许调用 applyRefund；
            4. 不要重复调用同一工具获取相同信息；
            5. 缺少订单号时直接向用户询问，不要编造订单号或订单信息。
            6. CONFIRM 阶段不会提供 applyRefund 工具，这是正常的：请先向用户复述订单信息并询问「是否确认退款」，
               用户明确确认后系统会进入执行阶段，不要因为没有退款工具就转人工。
            7. 只有业务规则明确不允许、关键信息多次追问仍拒不提供，或任务确实超出能力范围时，才调用
               escalate_to_human；申请会被系统拦截并要求继续澄清，请优先追问而不是转人工。
            8. 用户回复「嗯」「好的」这类模糊表达时，视为待确认，先追问一次明确确认，不要直接执行或转人工。
            所有回复使用中文，语气专业简洁。
            """;

    private static final Set<String> GENERAL_TOOLS = Set.of(
            "queryOrder", "queryLogistics", "createCrmTicket", "query_erp_purchase_order");

    /** 任何阶段都允许 AI 主动申请转人工 */
    private static final String ESCALATE_TOOL = "escalate_to_human";

    private final ChatClient chatClient;
    private final ChatOptions chatOptions;
    private final ToolRegistry toolRegistry;
    private final ToolGateway toolGateway;
    private final CsProperties properties;

    public RefundFsmAgent(ChatModel dashscopeChatModel, ToolRegistry toolRegistry,
                          ToolGateway toolGateway, CsProperties properties) {
        this.chatClient = ChatClient.builder(dashscopeChatModel).build();
        this.toolRegistry = toolRegistry;
        this.toolGateway = toolGateway;
        this.properties = properties;
        // 禁用框架自动工具执行，所有调用必须走 ToolGateway
        this.chatOptions = DashScopeChatOptions.builder().withProxyToolCalls(true).build();
    }

    @Override
    public String getSkillId() {
        return "REFUND_SKILL";
    }

    /**
     * @param message  用户输入
     * @param session  会话（状态机状态跨轮恢复）
     * @param category 业务分类，refund 走完整退款状态机，其余走通用查询模式
     * @param token    用户令牌，用于工具鉴权
     * @param history  裁剪后的历史消息
     */
    @Override
    public AgentRunResult run(String message, SessionContext session, String category,
                              String token, List<Message> history) {
        AgentFsm.State state = AgentFsm.parse(session.getAgentState());
        boolean refundFlow = "refund".equals(category) || state != AgentFsm.State.START;
        List<String> traces = new ArrayList<>();
        String orderId = resolveOrderId(message, session);

        // 多轮确认：明确确认才执行；模糊确认先追问；拒绝则取消
        if (refundFlow && (state == AgentFsm.State.VERIFY_ORDER || state == AgentFsm.State.CONFIRM)) {
            if (AgentFsm.isReject(message)) {
                resetSession(session);
                return new AgentRunResult("好的，已为您取消本次退款申请，订单保持原状态。还有其他可以帮您的吗？",
                        AgentFsm.State.FINISHED, traces, false, null);
            }
            if (AgentFsm.isStrongConfirm(message)) {
                state = AgentFsm.State.EXECUTE;
                traces.add("用户明确确认退款，状态机进入 EXECUTE");
            } else if (AgentFsm.isAmbiguousConfirm(message)) {
                // "嗯""好的"这类模糊表达：追问一次，不执行也不转人工
                persistState(session, AgentFsm.State.CONFIRM, orderId);
                session.resetEscalate();
                traces.add("模糊确认，追问用户明确确认");
                return new AgentRunResult(
                        "您是希望我为您提交这笔订单的退款吗？为避免误操作，请回复「确认退款」我再为您办理；"
                                + "如不需要，回复「不用了」即可。",
                        AgentFsm.State.CONFIRM, traces, false, null);
            }
        }

        List<Message> messages = new ArrayList<>();
        if (history != null) {
            messages.addAll(history);
        }
        messages.add(new UserMessage(message));

        LoopDetector loopDetector = new LoopDetector(properties.getAgent().getRepeatLimit());
        String answer = null;
        int consecutiveFailures = 0;

        int maxSteps = properties.getAgent().getMaxSteps();
        for (int step = 1; step <= maxSteps; step++) {
            Set<String> allowed = refundFlow ? AgentFsm.allowedTools(state) : GENERAL_TOOLS;
            if (allowed.isEmpty()) {
                break;
            }
            // AI 主动降级通道：任何阶段都可见
            Set<String> allowedWithEscalate = new java.util.HashSet<>(allowed);
            allowedWithEscalate.add(ESCALATE_TOOL);
            allowed = allowedWithEscalate;
            List<ToolCallback> callbacks = toolRegistry.callbacksOf(allowed);
            if (callbacks.isEmpty()) {
                return AgentRunResult.needHuman("当前业务工具不可用，为您转接人工客服处理。", state, traces, "TOOL_NOT_AVAILABLE");
            }

            // CoT 动态注入：把状态机约束写进每一轮的 prompt
            messages.add(new UserMessage(buildStepPrompt(state, orderId, step, allowed)));

            ChatResponse response;
            try {
                response = chatClient.prompt(new Prompt(messages, chatOptions))
                        .system(SYSTEM_PROMPT)
                        .tools(callbacks.toArray(new ToolCallback[0]))
                        .call()
                        .chatResponse();
            } catch (Exception e) {
                log.error("[CS-Agent] LLM 调用失败：{}", e.getMessage());
                persistState(session, state, orderId);
                return AgentRunResult.needHuman("系统繁忙，已为您转接人工客服。", state, traces, "LLM_ERROR");
            }

            AssistantMessage output = response.getResult().getOutput();
            List<AssistantMessage.ToolCall> toolCalls = output.getToolCalls();
            if (toolCalls == null || toolCalls.isEmpty()) {
                answer = output.getText();
                // 已核实订单但未确认：停在 CONFIRM，等待用户下一轮确认
                if (refundFlow && state == AgentFsm.State.VERIFY_ORDER && answer != null && answer.contains("确认")) {
                    state = AgentFsm.State.CONFIRM;
                }
                break;
            }

            StringBuilder observation = new StringBuilder();
            for (AssistantMessage.ToolCall call : toolCalls) {
                String toolName = call.name();
                Map<String, Object> arguments = parseArguments(call.arguments());
                // 跳步/越权防护：白名单之外的工具请求直接拒绝，不执行
                if (!allowed.contains(toolName)) {
                    observation.append("工具 ").append(toolName)
                            .append(" 在当前状态 ").append(state)
                            .append(" 下被禁止调用，请先完成当前阶段：").append(AgentFsm.describe(state)).append("\n");
                    traces.add("拦截越权工具调用：" + toolName);
                    continue;
                }
                // AI 主动申请转人工：不立即生效，需多次申请；未达阈值先拦截并要求继续澄清
                if (ESCALATE_TOOL.equals(toolName)) {
                    int attempt = session.incrementEscalate();
                    int required = properties.getAgent().getEscalateAttempts();
                    String reason = String.valueOf(arguments.getOrDefault("reason", "AI 判定无法自动处理"));
                    if (attempt >= required) {
                        session.resetEscalate();
                        traces.add("AI 第 " + attempt + " 次申请转人工，生效：" + reason);
                        persistState(session, state, orderId);
                        return AgentRunResult.needHuman("这个情况需要人工客服为您进一步核实，已为您转接。",
                                state, traces, "AI_ESCALATE");
                    }
                    traces.add("拦截 AI 转人工申请 " + attempt + "/" + required);
                    observation.append("【系统拦截】当前不满足转人工条件（第 ").append(attempt).append("/")
                            .append(required).append(" 次申请）。请先向用户澄清或追问确认，例如：")
                            .append("「您是否确认退款？回复『确认退款』即可办理」，不要重复调用 escalate_to_human。\n");
                    continue;
                }
                if (loopDetector.record(toolName, arguments)) {
                    observation.append("检测到重复调用 ").append(toolName).append("，已终止执行\n");
                    persistState(session, state, orderId);
                    return AgentRunResult.needHuman("检测到任务重复执行，为避免误操作已转人工处理。",
                            state, traces, "LOOP_DETECTED");
                }
                if (orderId != null && !arguments.containsKey("orderId")) {
                    arguments.put("orderId", orderId);
                }
                ToolResult result = toolGateway.execute(ToolInvocation.builder()
                        .toolName(toolName)
                        .arguments(arguments)
                        .token(token)
                        .sessionId(session.getSessionId())
                        .build());
                observation.append("工具 ").append(toolName).append(" 执行结果：").append(result.getContent()).append("\n");
                traces.add(toolName + (result.isSuccess() ? " 成功" : " 失败(" + result.getErrorCode() + ")"));
                session.getExecutedTools().add(toolName);

                // 工具连续失败：不无限重试，直接转人工（避免加剧下游拥堵）
                if (result.isSuccess()) {
                    consecutiveFailures = 0;
                } else if (++consecutiveFailures >= 2) {
                    persistState(session, state, orderId);
                    return AgentRunResult.needHuman("业务系统连续调用失败，为避免给您错误信息，已为您转接人工客服。",
                            state, traces, "TOOL_CONSECUTIVE_FAILURE");
                }
                if (result.isSuccess() && "queryOrder".equals(toolName)) {
                    String oid = String.valueOf(arguments.getOrDefault("orderId", ""));
                    if (!oid.isBlank()) {
                        orderId = oid;
                    }
                }
                state = AgentFsm.advanceAfterTool(state, toolName, result.isSuccess());
            }
            // ReAct 观察写回：作为下一轮 Thought 的上下文
            messages.add(new UserMessage("观察结果(Observation)：\n" + observation));
            if (state == AgentFsm.State.FINISHED) {
                // 让模型基于观察结果做最终总结
                continue;
            }
        }

        persistState(session, state, orderId);
        if (answer == null || answer.isBlank()) {
            if (state == AgentFsm.State.FINISHED) {
                answer = "已为您完成退款申请，退款将原路退回，请注意查收。还有其他可以帮您的吗？";
            } else {
                return AgentRunResult.needHuman("任务步骤过多，已为您转接人工客服继续处理。",
                        state, traces, "MAX_STEPS");
            }
        }
        // 正常推进一轮后重置转人工申请计数，避免跨轮累积
        session.resetEscalate();
        return new AgentRunResult(answer, state, traces, false, null);
    }

    private String buildStepPrompt(AgentFsm.State state, String orderId, int step, Set<String> allowed) {
        return """
                【任务状态跟踪】
                当前状态：%s（%s）
                当前订单号：%s
                本轮允许调用的工具：%s
                已执行步数：%d
                请先思考当前还缺什么信息，再决定调用工具还是直接回复用户。禁止调用白名单之外的工具。
                """.formatted(state, AgentFsm.describe(state),
                orderId == null ? "未提供" : orderId, allowed, step);
    }

    private String resolveOrderId(String message, SessionContext session) {
        Object existing = session.getAgentParams().get("orderId");
        if (existing != null) {
            return String.valueOf(existing);
        }
        java.util.regex.Matcher matcher = java.util.regex.Pattern
                .compile("(?<![0-9A-Za-z])([A-Za-z]{0,2}\\d{4,})(?![0-9A-Za-z])").matcher(message);
        return matcher.find() ? matcher.group(1) : null;
    }

    private Map<String, Object> parseArguments(String arguments) {
        if (arguments == null || arguments.isBlank()) {
            return new HashMap<>();
        }
        try {
            JSONObject jsonObject = JSONUtil.parseObj(arguments);
            return new HashMap<>(jsonObject);
        } catch (Exception e) {
            return new HashMap<>();
        }
    }

    private void persistState(SessionContext session, AgentFsm.State state, String orderId) {
        if (state == AgentFsm.State.FINISHED || state == AgentFsm.State.FAILED) {
            resetSession(session);
            return;
        }
        session.setAgentState(state.name());
        if (orderId != null) {
            session.getAgentParams().put("orderId", orderId);
        }
    }

    private void resetSession(SessionContext session) {
        session.setAgentState(null);
        session.getAgentParams().clear();
        session.resetEscalate();
        session.setAngerCount(0);
    }
}
