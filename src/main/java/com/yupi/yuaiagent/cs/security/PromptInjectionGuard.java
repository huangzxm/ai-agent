package com.yupi.yuaiagent.cs.security;

import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * Prompt 注入与危险指令防护：
 * 1) 用户输入进入编排流程前检测；
 * 2) LLM 产出的工具参数在执行前再次检测（不信任模型输出）。
 */
@Component
public class PromptInjectionGuard {

    private static final List<Rule> RULES = List.of(
            new Rule("IGNORE_INSTRUCTION", Pattern.compile(
                    "忽略(之前|以上|上面|前面|所有|全部)的?(指令|提示|规则|设定|要求)", Pattern.CASE_INSENSITIVE),
                    "尝试覆盖既有指令"),
            new Rule("IGNORE_INSTRUCTION_EN", Pattern.compile(
                    "ignore\\s+(all\\s+)?(previous|above|prior)\\s+(instructions|prompts|rules)", Pattern.CASE_INSENSITIVE),
                    "尝试覆盖既有指令"),
            new Rule("SYSTEM_PROMPT", Pattern.compile(
                    "(系统|system|developer)\\s*(提示词|prompt|指令)", Pattern.CASE_INSENSITIVE),
                    "尝试获取系统提示词"),
            new Rule("ROLE_OVERRIDE", Pattern.compile(
                    "(你现在是|从现在开始你是|扮演|假装你是).{0,16}(管理员|开发者|超级用户|root|管理员权限)",
                    Pattern.CASE_INSENSITIVE),
                    "尝试角色越权"),
            new Rule("SQL_INJECTION", Pattern.compile(
                    "(union\\s+select|or\\s+1\\s*=\\s*1|drop\\s+(table|database)|truncate\\s+table|delete\\s+from)",
                    Pattern.CASE_INSENSITIVE),
                    "疑似 SQL 注入"),
            new Rule("SHELL_INJECTION", Pattern.compile(
                    "(rm\\s+-rf\\s+/|;\s*(curl|wget|bash|sh)\\s|\\$\\(.*\\))", Pattern.CASE_INSENSITIVE),
                    "疑似命令注入"),
            new Rule("XSS", Pattern.compile("(<script|javascript\\s*:|onerror\\s*=)", Pattern.CASE_INSENSITIVE),
                    "疑似脚本注入"));

    public Optional<Rule> detect(String text) {
        if (text == null || text.isBlank()) {
            return Optional.empty();
        }
        for (Rule rule : RULES) {
            if (rule.pattern().matcher(text).find()) {
                return Optional.of(rule);
            }
        }
        return Optional.empty();
    }

    public boolean isInjection(String text) {
        return detect(text).isPresent();
    }

    /**
     * 检查工具参数：任何字符串参数命中规则即拒绝执行
     */
    public Optional<Rule> detectInArguments(Map<String, Object> arguments) {
        if (arguments == null || arguments.isEmpty()) {
            return Optional.empty();
        }
        for (Object value : arguments.values()) {
            if (value instanceof String text) {
                Optional<Rule> hit = detect(text);
                if (hit.isPresent()) {
                    return hit;
                }
            }
        }
        return Optional.empty();
    }

    /** 敏感参数脱敏，用于审计日志 */
    public Map<String, Object> maskForAudit(Map<String, Object> arguments) {
        Map<String, Object> masked = new LinkedHashMap<>();
        if (arguments == null) {
            return masked;
        }
        arguments.forEach((key, value) -> {
            if (value instanceof String text && text.length() > 80) {
                masked.put(key, text.substring(0, 80) + "…");
            } else {
                masked.put(key, value);
            }
        });
        return masked;
    }

    public record Rule(String code, Pattern pattern, String description) {
    }
}
