package com.yupi.yuaiagent.cs.agent;

import com.yupi.yuaiagent.cs.model.SessionContext;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.messages.Message;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Set;

/**
 * Agent 技能分发器：按 skillId 找到技能定义并选择执行器。
 * FSM 类技能（退款）走专用状态机实现，其余声明式技能走通用 ReAct 执行器。
 */
@Component
@Slf4j
public class SkillDispatcher {

    private final SkillRegistry skillRegistry;
    private final RefundFsmAgent refundFsmAgent;
    private final GenericSkillAgent genericSkillAgent;

    public SkillDispatcher(SkillRegistry skillRegistry, RefundFsmAgent refundFsmAgent,
                           GenericSkillAgent genericSkillAgent) {
        this.skillRegistry = skillRegistry;
        this.refundFsmAgent = refundFsmAgent;
        this.genericSkillAgent = genericSkillAgent;
    }

    public AgentSkill dispatch(String skillId) {
        SkillDefinition definition = skillRegistry.get(skillId);
        if (definition == null) {
            definition = skillRegistry.get(SkillRegistry.REFUND_SKILL);
        }
        final SkillDefinition target = definition;
        if (target == null || target.isFsm()) {
            return refundFsmAgent;
        }
        return new AgentSkill() {
            @Override
            public String getSkillId() {
                return target.getId();
            }

            @Override
            public AgentRunResult run(String message, SessionContext session, String category,
                                      String token, List<Message> history) {
                return genericSkillAgent.run(target, message, session, category, token, history);
            }
        };
    }

    public Set<String> skillIds() {
        return skillRegistry.ids();
    }
}
