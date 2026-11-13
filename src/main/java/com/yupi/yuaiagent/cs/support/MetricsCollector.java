package com.yupi.yuaiagent.cs.support;

import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.LongAdder;

/**
 * 全链路指标：路由分布、各路径耗时、缓存命中、降级次数、工具成功率、token 估算
 */
@Component
public class MetricsCollector {

    private final Map<String, LongAdder> counters = new ConcurrentHashMap<>();
    private final Map<String, AtomicLong> routeCounts = new ConcurrentHashMap<>();
    private final Map<String, PathStat> pathStats = new ConcurrentHashMap<>();

    public void increment(String key) {
        counters.computeIfAbsent(key, k -> new LongAdder()).increment();
    }

    public void recordRoute(String route) {
        routeCounts.computeIfAbsent(route, k -> new AtomicLong()).incrementAndGet();
    }

    public void recordPath(String path, long costMillis) {
        PathStat stat = pathStats.computeIfAbsent(path, k -> new PathStat());
        stat.count.increment();
        stat.totalMillis.add(costMillis);
        if (costMillis > 8000) {
            increment("slow_requests");
        }
    }

    public void recordTool(boolean success) {
        increment(success ? "tool_success" : "tool_failure");
    }

    public void recordFallback(String reason) {
        increment("fallback_total");
        increment("fallback_" + reason);
    }

    public void recordTokens(int tokens) {
        counters.computeIfAbsent("estimated_tokens", k -> new LongAdder()).add(tokens);
    }

    public Map<String, Object> snapshot() {
        Map<String, Object> result = new ConcurrentHashMap<>();
        Map<String, Long> counterView = new ConcurrentHashMap<>();
        counters.forEach((k, v) -> counterView.put(k, v.sum()));
        result.put("counters", counterView);
        result.put("routes", routeCounts);
        Map<String, Object> pathView = new ConcurrentHashMap<>();
        pathStats.forEach((path, stat) -> {
            long count = stat.count.sum();
            pathView.put(path, Map.of(
                    "count", count,
                    "avgMillis", count == 0 ? 0 : stat.totalMillis.sum() / count));
        });
        result.put("paths", pathView);
        long success = counterView.getOrDefault("tool_success", 0L);
        long failure = counterView.getOrDefault("tool_failure", 0L);
        result.put("toolSuccessRate", success + failure == 0 ? 1.0 : (double) success / (success + failure));
        return result;
    }

    private static class PathStat {
        private final LongAdder count = new LongAdder();
        private final LongAdder totalMillis = new LongAdder();
    }
}
