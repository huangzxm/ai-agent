package com.yupi.yuaiagent.cs.tool;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.URI;

/**
 * MCP 服务健康巡检：定时探测注册中心中的 Server，
 * 健康状态变化时联动工具注册表上下线，实现不重启的感知。
 */
@Component
@Slf4j
public class McpHealthChecker {

    private static final int CONNECT_TIMEOUT_MILLIS = 1200;

    private final McpServerRegistry serverRegistry;
    private final ToolRegistry toolRegistry;

    public McpHealthChecker(McpServerRegistry serverRegistry, ToolRegistry toolRegistry) {
        this.serverRegistry = serverRegistry;
        this.toolRegistry = toolRegistry;
    }

    @PostConstruct
    public void init() {
        checkAll();
    }

    @Scheduled(fixedDelay = 15000L, initialDelay = 15000L)
    public void checkAll() {
        for (McpServerRegistry.ServerInfo server : serverRegistry.list()) {
            boolean up = isReachable(server.getBaseUrl());
            boolean changed = !server.getStatus().equals(up ? "UP" : "DOWN");
            if (changed) {
                log.warn("[CS] MCP Server 状态变化 {} -> {}", server.getServerId(), up ? "UP" : "DOWN");
            }
            serverRegistry.updateStatus(server.getServerId(), up, toolRegistry.mcpToolCount());
        }
        // 全部 MCP Server 都不可用时，下线 MCP 工具，避免 Agent 继续尝试调用
        boolean anyUp = serverRegistry.list().stream().anyMatch(s -> "UP".equals(s.getStatus()));
        boolean previous = toolRegistry.isMcpAvailable();
        if (previous != anyUp) {
            toolRegistry.setMcpAvailable(anyUp);
            toolRegistry.refreshMcpTools();
            log.warn("[CS] MCP 工具可用状态切换为 {}", anyUp);
        }
    }

    /** 手动触发一次巡检（运营接口调用） */
    public void refreshNow() {
        checkAll();
        toolRegistry.refreshMcpTools();
    }

    private boolean isReachable(String baseUrl) {
        if (baseUrl == null || baseUrl.isBlank()) {
            return false;
        }
        try {
            URI uri = URI.create(baseUrl);
            int port = uri.getPort() == -1 ? ("https".equalsIgnoreCase(uri.getScheme()) ? 443 : 80) : uri.getPort();
            try (Socket socket = new Socket()) {
                socket.connect(new InetSocketAddress(uri.getHost(), port), CONNECT_TIMEOUT_MILLIS);
                return true;
            }
        } catch (Exception e) {
            return false;
        }
    }
}
