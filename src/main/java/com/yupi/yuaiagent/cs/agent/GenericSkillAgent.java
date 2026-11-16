package com.yupi.yuaiagent.cs.agent;

import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.alibaba.cloud.ai.dashscope.chat.DashScopeChatOptions;
import com.yupi.yuaiagent.cs.CsProperties;
import com.yupi.yuaiagent.cs.model.SessionContext;
import com.yupi.yuaiagent.cs.model.ToolInvocation;
import com.yupi.yuaiagent.cs.model.ToolResult;
import com.yupi.yuaiagent.cs.support.TokenUsageTracker;
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
 * 声明式技能的通用执行器：按 SkillDefinition 动态组装系统提示与工具白名单，
 * 走标准 ReAct 循环，工具调用统一经 ToolGateway（鉴权、状态校验、Observation 瘦身）。
 * 高风险流程（退款）仍由 RefundFsmAgent 的 FSM 实现处理。
 */
@Component
@Slf4j
public class GenericSkillAgent {

    private final ChatClient chatClient;
    private final ChatOptions chatOptions;
    private final ToolRegistry toolRegistry;
    private final ToolGateway toolGateway;
    private final TokenUsageTracker tokenUsageTracker;
    private final CsProperties properties;

    public GenericSkillAgent(ChatModel dashscopeChatModel, ToolRegistry toolRegistry,
                             ToolGateway toolGateway, TokenUsageTracker tokenUsageTracker,
                             CsProperties properties) {
        this.chatClient = ChatClient.builder(dashscopeChatModel).build();
        this.toolRegistry = toolRegistry;
        this.toolGateway = toolGateway;
        this.tokenUsageTracker = tokenUsageTracker;
        this.properties = properties;
        // 禁用框架自动工具执行，所有调用必须走 ToolGateway
        this.chatOptions = DashScopeChatOptions.builder().withProxyToolCalls(true).build();
    }

    public AgentRunResult run(SkillDefinition skill, String message, SessionContext session,
                              String category, String token, List<Message> history) {
        List<String> traces = new ArrayList<>();
        Set<String> allowed = toolRegistry.resolveToolNames(skill.getAllowedTools());
        List<ToolCallback> callbacks = toolRegistry.callbacksOf(allowed);
        if (callbacks.isEmpty()) {
            return new AgentRunResult("该技能暂未配置可用工具，请稍后再试或转人工处理。",
                    AgentFsm.State.FINISHED, traces, false, null);
        }

        List<Message> messages = new ArrayList<>();
        if (history != null) {
            messages.addAll(history);
        }
        messages.add(new UserMessage(message));

        LoopDetector loopDetector = new LoopDetector(properties.getAgent().getRepeatLimit());
        int maxSteps = skill.getMaxSteps() > 0 ? skill.getMaxSteps() : properties.getAgent().getMaxSteps();
        String answer = null;

        for (int step = 1; step <= maxSteps; step++) {
            messages.add(new UserMessage(buildStepPrompt(skill, allowed, step)));
            ChatResponse response;
            try {
                response = chatClient.prompt(new Prompt(messages, chatOptions))
                        .system(skill.getSystemPrompt())
                        .tools(callbacks.toArray(new ToolCallback[0]))
                        .call()
                        .chatResponse();
            } catch (Exception e) {
                log.error("[CS-Skill] LLM 调用失败 skill={} error={}", skill.getId(), e.getMessage());
                return AgentRunResult.needHuman("系统繁忙，已为您转接人工客服。",
                        AgentFsm.State.FINISHED, traces, "LLM_ERROR");
            }
            tokenUsageTracker.record(TokenUsageTracker.SCENE_AGENT, response.getMetadata().getUsage());

            AssistantMessage output = response.getResult().getOutput();
            List<AssistantMessage.ToolCall> toolCalls = output.getToolCalls();
            if (toolCalls == null || toolCalls.isEmpty()) {
                answer = output.getText();
                break;
            }

            StringBuilder observation = new StringBuilder();
            for (AssistantMessage.ToolCall call : toolCalls) {
                String toolName = call.name();
                Map<String, Object> arguments = parseArguments(call.arguments());

                // 技能工具白名单：模型请求未授权工具时直接拒绝，不执行
                if (!allowed.contains(toolName)) {
                    observation.append("工具 ").append(toolName)
                            .append(" 不在当前技能授权范围内，已拒绝。请只使用：").append(allowed).append("\n");
                    traces.add("拦截未授权工具：" + toolName);
                    continue;
                }
                if (loopDetector.record(toolName, arguments)) {
                    session.setPendingHumanOffer(true);
                    traces.add("重复调用工具，询问用户是否转人工");
                    return new AgentRunResult(
                            "我刚刚尝试了相同的操作，这个问题可能无法自动完成。需要为您转接人工客服吗？"
                                    + "回复「转人工」即可；也可以补充更多信息让我再试一次。",
                            AgentFsm.State.FINISHED, traces, false, null);
                }

                ToolResult result = toolGateway.execute(ToolInvocation.builder()
                        .toolName(toolName)
                        .arguments(arguments)
                        .token(token)
                        .sessionId(session.getSessionId())
                        .build());
                observation.append("工具 ").append(toolName).append(" 执行结果：")
                        .append(result.getContent()).append("\n");
                traces.add(toolName + (result.isSuccess() ? " 成功" : " 失败(" + result.getErrorCode() + ")"));
                session.getExecutedTools().add(toolName);
            }
            messages.add(new UserMessage("观察结果(Observation)：\n" + observation));
        }

        if (answer == null || answer.isBlank()) {
            return AgentRunResult.needHuman("任务步骤过多，已为您转接人工客服继续处理。",
                    AgentFsm.State.FINISHED, traces, "MAX_STEPS");
        }
        session.resetEscalate();
        return new AgentRunResult(answer, AgentFsm.State.FINISHED, traces, false, null);
    }

    private String buildStepPrompt(SkillDefinition skill, Set<String> allowed, int step) {
        return """
                【技能：%s】
                当前可用工具：%s
                当前步骤：%d
                要求：
                1. 只能调用上述工具，不要编造工具或参数；
                2. 涉及写入/变更的操作，必须先向用户确认再执行；
                3. 不要重复调用相同工具获取相同信息；
                4. 信息不足时先向用户追问，不要臆测。
                """.formatted(skill.getName(), allowed, step);
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
}
