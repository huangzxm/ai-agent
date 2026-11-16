package com.yupi.yuaiagent.cs.tool;

import jakarta.annotation.PostConstruct;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * MCP 服务注册中心：记录业务方上线的 MCP Server 及其健康状态。
 * 生产可替换为 Nacos/Consul 等注册中心，业务方发布新 Server 后在此注册即可被 Agent 感知。
 */
@Component
@Slf4j
public class McpServerRegistry {

    private final Map<String, ServerInfo> servers = new ConcurrentHashMap<>();

    @Value("${spring.ai.mcp.client.sse.connections.servers1.url:}")
    private String defaultServerUrl;

    @PostConstruct
    public void init() {
        if (defaultServerUrl != null && !defaultServerUrl.isBlank()) {
            register("servers1", defaultServerUrl, "默认 MCP 服务（配置文件）");
        }
    }

    public void register(String serverId, String baseUrl, String description) {
        servers.put(serverId, new ServerInfo(serverId, baseUrl, description));
        log.info("[CS] MCP Server 注册：{} -> {}", serverId, baseUrl);
    }

    public void unregister(String serverId) {
        servers.remove(serverId);
    }

    public List<ServerInfo> list() {
        return servers.values().stream()
                .sorted(java.util.Comparator.comparing(ServerInfo::getServerId))
                .toList();
    }

    public void updateStatus(String serverId, boolean up, int toolCount) {
        ServerInfo info = servers.get(serverId);
        if (info == null) {
            return;
        }
        info.setStatus(up ? "UP" : "DOWN");
        info.setToolCount(toolCount);
        info.setLastCheckAt(System.currentTimeMillis());
    }

    @Data
    public static class ServerInfo {
        private final String serverId;
        private final String baseUrl;
        private final String description;
        private volatile String status = "UNKNOWN";
        private volatile int toolCount;
        private volatile long lastCheckAt;

        public ServerInfo(String serverId, String baseUrl, String description) {
            this.serverId = serverId;
            this.baseUrl = baseUrl;
            this.description = description;
        }
    }
}
