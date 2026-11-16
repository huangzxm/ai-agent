package com.yupi.yuaiagent.cs.agent;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * 声明式技能定义：一个 YAML 文件描述一个业务技能。
 * allowedTools 支持具体工具名、mcp:*（全部 MCP 工具）、mcp:工具名（指定 MCP 工具）。
 */
@Data
public class SkillDefinition {

    private String id;

    private String name;

    private String description;

    /** 业务分类，与路由输出的 category 对齐 */
    private String category;

    /** 技能系统提示词 */
    private String systemPrompt;

    /** 允许调用的工具，支持 mcp: 前缀 */
    private List<String> allowedTools = new ArrayList<>();

    private int maxSteps = 8;

    /** 是否使用专用 FSM 实现（退款等高风控流程为 true） */
    private boolean fsm = false;
}
