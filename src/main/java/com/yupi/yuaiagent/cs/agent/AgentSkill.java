package com.yupi.yuaiagent.cs.agent;

import com.yupi.yuaiagent.cs.model.SessionContext;
import org.springframework.ai.chat.messages.Message;

import java.util.List;

/**
 * Agent 技能接口：新增业务只需新增实现类，无需改动路由核心逻辑
 */
public interface AgentSkill {

    /** 技能标识，如 REFUND_SKILL */
    String getSkillId();

    /**
     * 执行技能
     *
     * @param message  用户输入
     * @param session  会话上下文（状态机跨轮恢复）
     * @param category 业务分类
     * @param token    用户令牌
     * @param history  裁剪后的历史消息
     */
    AgentRunResult run(String message, SessionContext session, String category,
                       String token, List<Message> history);
}
