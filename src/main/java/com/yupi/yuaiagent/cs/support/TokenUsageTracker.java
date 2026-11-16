package com.yupi.yuaiagent.cs.support;

import com.yupi.yuaiagent.cs.CsProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.metadata.Usage;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.LongAdder;

/**
 * 按场景统计真实 Token 用量：路由、查询重写、RAG 生成、Agent 推理等。
 * 数据来自 ChatResponse.metadata.usage，而非字符数估算。
 */
@Component
@Slf4j
public class TokenUsageTracker {

    public static final String SCENE_ROUTER = "router";
    public static final String SCENE_REWRITE = "rewrite";
    public static final String SCENE_RAG = "rag_answer";
    public static final String SCENE_AGENT = "agent";

    private final CsProperties properties;

    private final Map<String, SceneUsage> scenes = new ConcurrentHashMap<>();

    public TokenUsageTracker(CsProperties properties) {
        this.properties = properties;
    }

    public void record(String scene, Usage usage) {
        if (usage == null) {
            return;
        }
        SceneUsage stat = scenes.computeIfAbsent(scene, k -> new SceneUsage());
        int prompt = usage.getPromptTokens() == null ? 0 : usage.getPromptTokens();
        int completion = usage.getCompletionTokens() == null ? 0 : usage.getCompletionTokens();
        int total = usage.getTotalTokens() == null ? prompt + completion : usage.getTotalTokens();
        stat.calls.increment();
        stat.promptTokens.add(prompt);
        stat.completionTokens.add(completion);
        stat.totalTokens.add(total);
        log.debug("[CS] Token 用量 scene={} prompt={} completion={} total={}", scene, prompt, completion, total);
    }

    public Map<String, Object> snapshot() {
        Map<String, Object> sceneView = new LinkedHashMap<>();
        long promptSum = 0;
        long completionSum = 0;
        long totalSum = 0;
        long callSum = 0;

        for (Map.Entry<String, SceneUsage> entry : scenes.entrySet()) {
            SceneUsage stat = entry.getValue();
            long prompt = stat.promptTokens.sum();
            long completion = stat.completionTokens.sum();
            long total = stat.totalTokens.sum();
            long calls = stat.calls.sum();
            sceneView.put(entry.getKey(), Map.of(
                    "calls", calls,
                    "promptTokens", prompt,
                    "completionTokens", completion,
                    "totalTokens", total,
                    "avgTokensPerCall", calls == 0 ? 0 : total / calls));
            promptSum += prompt;
            completionSum += completion;
            totalSum += total;
            callSum += calls;
        }

        double inputPrice = properties.getToken().getInputPricePer1k();
        double outputPrice = properties.getToken().getOutputPricePer1k();
        double estimatedCost = promptSum / 1000.0 * inputPrice + completionSum / 1000.0 * outputPrice;

        Map<String, Object> totals = new LinkedHashMap<>();
        totals.put("calls", callSum);
        totals.put("promptTokens", promptSum);
        totals.put("completionTokens", completionSum);
        totals.put("totalTokens", totalSum);
        totals.put("avgTokensPerCall", callSum == 0 ? 0 : totalSum / callSum);
        totals.put("estimatedCost", Math.round(estimatedCost * 10000) / 10000.0);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("scenes", sceneView);
        result.put("totals", totals);
        result.put("price", Map.of(
                "inputPer1k", inputPrice,
                "outputPer1k", outputPrice,
                "currency", "CNY"));
        return result;
    }

    private static class SceneUsage {
        private final LongAdder calls = new LongAdder();
        private final LongAdder promptTokens = new LongAdder();
        private final LongAdder completionTokens = new LongAdder();
        private final LongAdder totalTokens = new LongAdder();
    }
}
