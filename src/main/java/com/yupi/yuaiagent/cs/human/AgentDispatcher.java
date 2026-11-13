package com.yupi.yuaiagent.cs.human;

import com.yupi.yuaiagent.cs.CsProperties;
import jakarta.annotation.PostConstruct;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 模拟坐席调度：按当前负载分配最少忙碌的坐席
 */
@Component
public class AgentDispatcher {

    private final CsProperties properties;

    /** 坐席 → 当前处理的转接单数 */
    private final Map<String, AtomicInteger> load = new ConcurrentHashMap<>();

    public AgentDispatcher(CsProperties properties) {
        this.properties = properties;
    }

    @PostConstruct
    public void init() {
        int count = Math.max(1, properties.getHuman().getAgentCount());
        for (int i = 1; i <= count; i++) {
            load.put(String.format("seat-%02d", i), new AtomicInteger());
        }
    }

    /**
     * 分配负载最小的坐席
     */
    public String assign() {
        return load.entrySet().stream()
                .min(Comparator.comparingInt(e -> e.getValue().get()))
                .map(Map.Entry::getKey)
                .orElse("seat-01");
    }

    public void occupy(String agentId) {
        load.computeIfAbsent(agentId, k -> new AtomicInteger()).incrementAndGet();
    }

    public void release(String agentId) {
        AtomicInteger counter = load.get(agentId);
        if (counter != null && counter.get() > 0) {
            counter.decrementAndGet();
        }
    }

    public int queueDepth() {
        return load.values().stream().mapToInt(AtomicInteger::get).sum();
    }

    public List<Map<String, Object>> status() {
        List<Map<String, Object>> result = new ArrayList<>();
        load.forEach((agentId, counter) -> result.add(Map.of(
                "agentId", agentId,
                "load", counter.get(),
                "status", counter.get() == 0 ? "IDLE" : "BUSY")));
        return result;
    }
}
