package com.yupi.yuaiagent.cs.agent;

import com.yupi.yuaiagent.cs.CsProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.ResourcePatternResolver;
import org.springframework.stereotype.Component;
import org.yaml.snakeyaml.Yaml;

import java.io.InputStream;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 技能加载器：扫描 classpath:skills/*.yml，解析为 SkillDefinition。
 * 修改技能文件后调用 SkillRegistry.refresh() 即可生效，无需重启。
 */
@Component
@Slf4j
public class SkillLoader {

    private final ResourcePatternResolver resourcePatternResolver;
    private final CsProperties properties;
    private final Yaml yaml = new Yaml();

    public SkillLoader(ResourcePatternResolver resourcePatternResolver, CsProperties properties) {
        this.resourcePatternResolver = resourcePatternResolver;
        this.properties = properties;
    }

    public Map<String, SkillDefinition> loadAll() {
        Map<String, SkillDefinition> definitions = new LinkedHashMap<>();
        if (!properties.getSkill().isEnabled()) {
            return definitions;
        }
        try {
            Resource[] resources = resourcePatternResolver.getResources(properties.getSkill().getLocation());
            for (Resource resource : resources) {
                try (InputStream inputStream = resource.getInputStream()) {
                    Map<String, Object> map = yaml.load(inputStream);
                    SkillDefinition definition = toDefinition(map);
                    if (definition != null) {
                        definitions.put(definition.getId(), definition);
                        log.info("[CS] 加载技能 {} ({})，工具={}", definition.getId(),
                                definition.getName(), definition.getAllowedTools());
                    }
                }
            }
        } catch (Exception e) {
            log.warn("[CS] 技能文件加载失败：{}", e.getMessage());
        }
        return definitions;
    }

    @SuppressWarnings("unchecked")
    private SkillDefinition toDefinition(Map<String, Object> map) {
        if (map == null) {
            return null;
        }
        Object id = map.get("id");
        if (id == null || String.valueOf(id).isBlank()) {
            return null;
        }
        SkillDefinition definition = new SkillDefinition();
        definition.setId(String.valueOf(id).trim());
        definition.setName(asString(map.get("name"), definition.getId()));
        definition.setDescription(asString(map.get("description"), ""));
        definition.setCategory(asString(map.get("category"), null));
        definition.setSystemPrompt(asString(map.get("systemPrompt"), ""));
        Object maxSteps = map.get("maxSteps");
        if (maxSteps instanceof Number number) {
            definition.setMaxSteps(number.intValue());
        }
        Object fsm = map.get("fsm");
        if (fsm instanceof Boolean value) {
            definition.setFsm(value);
        }
        Object tools = map.get("allowedTools");
        if (tools instanceof List<?> list) {
            definition.setAllowedTools(list.stream().map(String::valueOf).toList());
        }
        return definition;
    }

    private String asString(Object value, String defaultValue) {
        return value == null ? defaultValue : String.valueOf(value);
    }
}
