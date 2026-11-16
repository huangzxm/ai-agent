package com.yupi.yuaiagent.cs.core;

import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 语义缓存存储（对应生产架构中的 PG 侧）：
 * 存问题向量、原始问答与业务分类，提供余弦相似度 KNN 检索。
 * 这里用内存实现，接口与 pgvector 版本对齐，替换时只改本类。
 */
@Component
public class SemanticCacheStore {

    public record CachedAnswer(String id, String question, String answer,
                               float[] embedding, String category, long createdAt) {
    }

    private final Map<String, CachedAnswer> store = new ConcurrentHashMap<>();

    /**
     * 语义检索：返回相似度达到阈值的最接近问答
     */
    public Optional<CachedAnswer> knn(float[] queryVector, double similarityThreshold) {
        if (queryVector == null || store.isEmpty()) {
            return Optional.empty();
        }
        CachedAnswer best = null;
        double bestScore = similarityThreshold;
        for (CachedAnswer candidate : store.values()) {
            double score = cosineSimilarity(queryVector, candidate.embedding());
            if (score >= bestScore) {
                bestScore = score;
                best = candidate;
            }
        }
        return Optional.ofNullable(best);
    }

    public String save(String question, String answer, float[] embedding, String category) {
        String id = UUID.randomUUID().toString().replace("-", "").substring(0, 16);
        store.put(id, new CachedAnswer(id, question, answer, embedding, category, System.currentTimeMillis()));
        return id;
    }

    public void delete(String id) {
        store.remove(id);
    }

    public void clear() {
        store.clear();
    }

    public int size() {
        return store.size();
    }

    /** 清理创建时间超过指定时长的冷数据（对应 PG 侧空间回收） */
    public int evictOlderThan(long millis) {
        long deadline = System.currentTimeMillis() - millis;
        int before = store.size();
        store.entrySet().removeIf(e -> e.getValue().createdAt() < deadline);
        return before - store.size();
    }

    private double cosineSimilarity(float[] a, float[] b) {
        if (a == null || b == null || a.length != b.length || a.length == 0) {
            return 0.0;
        }
        double dot = 0.0;
        double normA = 0.0;
        double normB = 0.0;
        for (int i = 0; i < a.length; i++) {
            dot += a[i] * b[i];
            normA += a[i] * a[i];
            normB += b[i] * b[i];
        }
        if (normA == 0 || normB == 0) {
            return 0.0;
        }
        return dot / (Math.sqrt(normA) * Math.sqrt(normB));
    }
}
