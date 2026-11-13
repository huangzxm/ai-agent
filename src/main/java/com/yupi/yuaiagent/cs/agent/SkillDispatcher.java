package com.yupi.yuaiagent.cs.agent;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Agent 技能分发器：按 skillId 分发到具体技能实现，支持横向扩展
 */
@Component
@Slf4j
public class SkillDispatcher {

    private final Map<String, AgentSkill> skills = new HashMap<>();

    /** 未指定技能或技能未注册时的兜底技能 */
    private AgentSkill defaultSkill;

    public SkillDispatcher(List<AgentSkill> skillList) {
        for (AgentSkill skill : skillList) {
            skills.put(skill.getSkillId(), skill);
            if ("REFUND_SKILL".equals(skill.getSkillId())) {
                defaultSkill = skill;
            }
        }
        log.info("[CS] 已注册 Agent 技能：{}", skills.keySet());
    }

    public AgentSkill dispatch(String skillId) {
        if (skillId != null && skills.containsKey(skillId)) {
            return skills.get(skillId);
        }
        return defaultSkill;
    }

    public Set<String> skillIds() {
        return skills.keySet();
    }
}
