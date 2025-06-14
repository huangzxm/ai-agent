package com.yupi.yuaiagent.agent;

import cn.hutool.core.util.StrUtil;
import com.yupi.yuaiagent.agent.model.AgentState;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import opennlp.tools.util.StringUtil;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;

import java.util.ArrayList;
import java.util.List;

/**
 * 抽象基础代理类，用于管理代理状态和执行流程。
 * 提供状态转换、内存管理和基于步骤的执行循环的基础功能。
 * 子类必须实现step方法。
 */
@Data
@Slf4j
public abstract class BaseAgent {

    // 核心属性
    protected AgentState state = AgentState.IDLE; // 代理状态

    private String name; // 代理名称

    private String systemPrompt; // 系统提示语
    private String nextStepPrompt; // 下一步提示语

    private int currentStep; // 当前步骤
    private int maxSteps; // 最大步骤

    private ChatClient chatClient; //类似于llm大模型

    private List<Message> messageList = new ArrayList<>(); // 对话记忆

    /**
     * 执行代理的主要方法，根据当前状态执行相应的步骤。
     * 子类必须实现step方法。
     *
     * @param userPrompt
     * @return
     */
    private String run(String userPrompt) {
        //1 检验参数
        if (this.state == AgentState.IDLE) {
            throw new RuntimeException("Cannot not agent from status: " + this.state);
        }
        if (StrUtil.isBlank(userPrompt)) {
            throw new RuntimeException("userPrompt run agent with empty user prompt");
        }
        //2 执行流程，更改状态
        this.state = AgentState.RUNNING;
        messageList.add(new UserMessage(userPrompt)); // 加入用户输入
        List<String> results = new ArrayList<>(); // 存储结果
        try {
            for (int i = 0; i < maxSteps && state != AgentState.FINISHED; i++) {
                int stepNum = i + 1;
                currentStep = stepNum;
                log.info("Executing step {}/{}", stepNum, maxSteps); // 打印当前步骤

                String stepResult = step(); //单步执行
                String result = "Step " + stepNum + ": " + stepResult;
                results.add(result);
            }
            if (currentStep >= maxSteps) {
                state = AgentState.FINISHED;
                results.add("Terminated: Reached max steps (" + maxSteps + ")");
            }
            return String.join("\n", results);
        }catch (Exception e) {
            state = AgentState.ERROR;
            log.error("error executing agent", e);
            return "执行错误" + e.getMessage();
        }finally {
            cleanup(); // 清理资源
        }
    }


    /**
     * 定义单个步骤
     *
     * @return
     */
    public abstract String step();

    /**
     * 清理资源
     */
    protected void cleanup() {
        // 子类可以重写此方法来清理资源
    }
}
