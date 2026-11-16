package com.yupi.yuaiagent.cs.rag;

import cn.hutool.http.HttpResponse;
import cn.hutool.http.HttpUtil;
import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.yupi.yuaiagent.cs.CsProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 精排（Cross-Encoder 思路）：
 * - provider=local：内置轻量打分（词项覆盖率 + 向量原始分），无外部依赖，保证可运行
 * - provider=dashscope：调用 gte-rerank，失败自动回退 local
 */
@Component
@Slf4j
public class Reranker {

    private static final String RERANK_URL =
            "https://dashscope.aliyuncs.com/api/v1/services/rerank/text-rerank/text-rerank";

    private final CsProperties properties;
    private final Bm25Index bm25Index;
    private final String apiKey;

    public Reranker(CsProperties properties, Bm25Index bm25Index,
                    @Value("${spring.ai.dashscope.api-key:}") String apiKey) {
        this.properties = properties;
        this.bm25Index = bm25Index;
        this.apiKey = apiKey;
    }

    public List<RerankedDocument> rerank(String query, List<Document> candidates) {
        if (candidates == null || candidates.isEmpty()) {
            return List.of();
        }
        CsProperties.Rerank config = properties.getRag().getRerank();
        if ("dashscope".equalsIgnoreCase(config.getProvider()) && apiKey != null && !apiKey.isBlank()) {
            List<RerankedDocument> remote = remoteRerank(query, candidates, config);
            if (!remote.isEmpty()) {
                return remote;
            }
            log.warn("[CS] DashScope 精排不可用，回退本地精排");
        }
        return localRerank(query, candidates);
    }

    /**
     * 本地精排：关键词覆盖率为主，向量原始分为辅。用于保证离线/降级时链路完整。
     */
    private List<RerankedDocument> localRerank(String query, List<Document> candidates) {
        List<String> queryTokens = bm25Index.queryTokens(query);
        List<RerankedDocument> scored = new ArrayList<>(candidates.size());
        for (Document document : candidates) {
            String text = document.getText() == null ? "" : document.getText();
            double coverage;
            if (queryTokens.isEmpty()) {
                coverage = 0.0;
            } else {
                long matched = queryTokens.stream().filter(text::contains).count();
                coverage = (double) matched / queryTokens.size();
            }
            double vectorScore = vectorScoreOf(document);
            double score = 0.65 * coverage + 0.35 * vectorScore;
            scored.add(new RerankedDocument(document, score));
        }
        scored.sort(Comparator.comparingDouble(RerankedDocument::score).reversed());
        return scored;
    }

    private List<RerankedDocument> remoteRerank(String query, List<Document> candidates,
                                                CsProperties.Rerank config) {
        try {
            JSONObject input = new JSONObject();
            input.set("query", query);
            input.set("documents", candidates.stream().map(Document::getText).toList());

            JSONObject parameters = new JSONObject();
            parameters.set("return_documents", false);
            parameters.set("top_n", candidates.size());

            JSONObject body = new JSONObject();
            body.set("model", config.getModel());
            body.set("input", input);
            body.set("parameters", parameters);

            HttpResponse response = HttpUtil.createPost(RERANK_URL)
                    .header("Authorization", "Bearer " + apiKey)
                    .header("Content-Type", "application/json")
                    .body(body.toString())
                    .timeout(config.getTimeoutMillis())
                    .execute();
            if (!response.isOk()) {
                log.warn("[CS] 精排接口返回 {}", response.getStatus());
                return List.of();
            }
            JSONArray results = JSONUtil.parseObj(response.body())
                    .getJSONObject("output").getJSONArray("results");
            Map<Integer, Double> scoreByIndex = new HashMap<>();
            for (int i = 0; i < results.size(); i++) {
                JSONObject item = results.getJSONObject(i);
                scoreByIndex.put(item.getInt("index"), item.getDouble("relevance_score"));
            }
            List<RerankedDocument> scored = new ArrayList<>();
            scoreByIndex.forEach((index, score) -> {
                if (index >= 0 && index < candidates.size()) {
                    scored.add(new RerankedDocument(candidates.get(index), score));
                }
            });
            scored.sort(Comparator.comparingDouble(RerankedDocument::score).reversed());
            log.info("[CS] DashScope 精排完成，候选={}", scored.size());
            return scored;
        } catch (Exception e) {
            log.warn("[CS] 精排调用失败：{}", e.getMessage());
            return List.of();
        }
    }

    private double vectorScoreOf(Document document) {
        Double score = document.getScore();
        if (score != null) {
            return score;
        }
        Object distance = document.getMetadata().get("distance");
        return distance instanceof Number number ? 1 - number.doubleValue() : 0.0;
    }

    public record RerankedDocument(Document document, double score) {
    }
}
