package com.yupi.yuaiagent.cs.tool;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.model.function.FunctionCallback;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.ai.tool.ToolCallbacks;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 工具注册中心：本地业务工具 + MCP 远程工具统一注册，支持热插拔刷新
 */
@Component
@Slf4j
public class ToolRegistry {

    private final CsBusinessTools csBusinessTools;
    private final ObjectProvider<ToolCallbackProvider> mcpToolProvider;

    /** toolName → callback */
    private final Map<String, ToolCallback> tools = new ConcurrentHashMap<>();
    /** toolName → 来源 local/mcp */
    private final Map<String, String> toolSources = new ConcurrentHashMap<>();

    public ToolRegistry(CsBusinessTools csBusinessTools, ObjectProvider<ToolCallbackProvider> mcpToolProvider) {
        this.csBusinessTools = csBusinessTools;
        this.mcpToolProvider = mcpToolProvider;
    }

    @PostConstruct
    public void init() {
        registerLocalTools();
        refreshMcpTools();
    }

    private void registerLocalTools() {
        for (ToolCallback callback : ToolCallbacks.from(csBusinessTools)) {
            String name = callback.getToolDefinition().name();
            tools.put(name, callback);
            toolSources.put(name, "local");
        }
        log.info("[CS] 本地业务工具注册完成：{}", toolSources.keySet());
    }

    /**
     * 刷新 MCP 工具：MCP 服务上下线后调用即可热插拔，无需重启主应用
     */
    public synchronized void refreshMcpTools() {
        toolSources.entrySet().removeIf(e -> {
            if ("mcp".equals(e.getValue())) {
                tools.remove(e.getKey());
                return true;
            }
            return false;
        });
        ToolCallbackProvider provider = mcpToolProvider.getIfAvailable();
        if (provider == null) {
            log.info("[CS] 未发现 MCP 工具提供者");
            return;
        }
        try {
            FunctionCallback[] mcpTools = provider.getToolCallbacks();
            if (mcpTools != null) {
                for (FunctionCallback callback : mcpTools) {
                    if (!(callback instanceof ToolCallback toolCallback)) {
                        log.warn("[CS] MCP 工具 {} 不是 ToolCallback 实现，跳过注册", callback.getName());
                        continue;
                    }
                    String name = toolCallback.getToolDefinition().name();
                    tools.put(name, toolCallback);
                    toolSources.put(name, "mcp");
                }
                log.info("[CS] MCP 工具注册完成，当前工具清单：{}", tools.keySet());
            }
        } catch (Exception e) {
            log.warn("[CS] MCP 工具加载失败（不影响本地工具）：{}", e.getMessage());
        }
    }

    public ToolCallback get(String toolName) {
        return tools.get(toolName);
    }

    public boolean contains(String toolName) {
        return tools.containsKey(toolName);
    }

    public Set<String> toolNames() {
        return tools.keySet();
    }

    public List<ToolCallback> callbacksOf(Collection<String> names) {
        return names.stream().filter(tools::containsKey).map(tools::get).toList();
    }

    public Map<String, String> sources() {
        return Map.copyOf(toolSources);
    }
}
