package com.yupi.yuaiagent.cs.agent;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 技能注册表：合并内置技能与 YAML 声明式技能（同名覆盖），支持热刷新。
 */
@Component
@Slf4j
public class SkillRegistry {

    public static final String REFUND_SKILL = "REFUND_SKILL";
    public static final String ORDER_SKILL = "ORDER_SKILL";
    public static final String ERP_SKILL = "ERP_SKILL";

    private final SkillLoader skillLoader;

    private volatile Map<String, SkillDefinition> definitions = Map.of();

    public SkillRegistry(SkillLoader skillLoader) {
        this.skillLoader = skillLoader;
    }

    @PostConstruct
    public void init() {
        refresh();
    }

    public synchronized void refresh() {
        Map<String, SkillDefinition> merged = new LinkedHashMap<>(builtinSkills());
        Map<String, SkillDefinition> loaded = skillLoader.loadAll();
        merged.putAll(loaded);
        this.definitions = Map.copyOf(merged);
        log.info("[CS] 技能注册完成：{}", definitions.keySet());
    }

    public SkillDefinition get(String skillId) {
        return skillId == null ? null : definitions.get(skillId);
    }

    public Collection<SkillDefinition> all() {
        return definitions.values();
    }

    public Set<String> ids() {
        return definitions.keySet();
    }

    /**
     * 内置兜底技能：YAML 缺失或被误删时仍可运行
     */
    private Map<String, SkillDefinition> builtinSkills() {
        Map<String, SkillDefinition> builtin = new LinkedHashMap<>();
        builtin.put(REFUND_SKILL, skill(REFUND_SKILL, "退款办理", "refund", true,
                "你是电商售后退款专员，严格按状态机执行退款，用户明确确认后才可提交退款。",
                List.of("queryOrder", "queryLogistics", "applyRefund")));
        builtin.put(ORDER_SKILL, skill(ORDER_SKILL, "订单查询", "order", false,
                "你是订单查询助手，只能查询订单与物流信息，不能修改订单。",
                List.of("queryOrder", "queryLogistics", "createCrmTicket")));
        builtin.put(ERP_SKILL, skill(ERP_SKILL, "采购单查询", "erp", false,
                "你是采购单查询助手，回答以状态历史中的异常反馈为准，不要根据最后一条状态臆测。",
                List.of("query_erp_purchase_order")));
        return builtin;
    }

    private SkillDefinition skill(String id, String name, String category, boolean fsm,
                                  String systemPrompt, List<String> tools) {
        SkillDefinition definition = new SkillDefinition();
        definition.setId(id);
        definition.setName(name);
        definition.setDescription(name);
        definition.setCategory(category);
        definition.setFsm(fsm);
        definition.setSystemPrompt(systemPrompt);
        definition.setAllowedTools(tools);
        return definition;
    }
}
