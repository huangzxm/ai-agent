package com.yupi.yuaiagent.cs.support;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 轻量熔断器：连续失败达阈值后打开，冷却期内直接拒绝，冷却结束进入半开探测。
 * 用于向量库与 MCP 工具这类「不可降级的基础设施」。
 */
@Component
@Slf4j
public class ServiceCircuitBreaker {

    private static final int FAILURE_THRESHOLD = 3;
    private static final long COOL_DOWN_MILLIS = 30_000L;

    private final Map<String, State> states = new ConcurrentHashMap<>();

    /**
     * @return true 表示允许调用
     */
    public boolean allow(String service) {
        State state = states.computeIfAbsent(service, k -> new State());
        if (!state.open) {
            return true;
        }
        if (System.currentTimeMillis() - state.openedAt.get() >= COOL_DOWN_MILLIS) {
            log.info("[CS-熔断] {} 进入半开探测", service);
            return true;
        }
        log.warn("[CS-熔断] {} 处于熔断状态，直接拒绝调用", service);
        return false;
    }

    public void recordSuccess(String service) {
        State state = states.computeIfAbsent(service, k -> new State());
        if (state.open) {
            log.info("[CS-熔断] {} 探测成功，熔断关闭", service);
        }
        state.failures.set(0);
        state.open = false;
    }

    public void recordFailure(String service) {
        State state = states.computeIfAbsent(service, k -> new State());
        int failures = state.failures.incrementAndGet();
        if (failures >= FAILURE_THRESHOLD && !state.open) {
            state.open = true;
            state.openedAt.set(System.currentTimeMillis());
            log.error("[CS-熔断] {} 连续失败 {} 次，熔断开启 {}s", service, failures, COOL_DOWN_MILLIS / 1000);
        }
    }

    public Map<String, Object> status() {
        Map<String, Object> result = new ConcurrentHashMap<>();
        states.forEach((name, state) -> result.put(name, Map.of(
                "open", state.open,
                "failures", state.failures.get(),
                "openedAt", state.openedAt.get())));
        return result;
    }

    private static class State {
        private final AtomicInteger failures = new AtomicInteger();
        private final AtomicLong openedAt = new AtomicLong();
        private volatile boolean open;
    }
}
