package com.yupi.yuaiagent.cs.core;

import com.yupi.yuaiagent.cs.CsProperties;
import com.yupi.yuaiagent.cs.support.MetricsCollector;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * 语义缓存门面：向量语义匹配（PG 侧）+ TTL/热点晋升（Redis 侧）。
 *
 * 相比精确字符串缓存，「怎么改密码」与「如何修改密码」也能命中同一条缓存。
 * 个性化问题（含订单号等）不写入缓存，避免跨用户串答案。
 */
@Component
@Slf4j
public class SemanticResponseCache {

    private final EmbeddingModel embeddingModel;
    private final SemanticCacheStore store;
    private final CacheLifecycleManager lifecycle;
    private final ResponseCache responseCache;
    private final MetricsCollector metricsCollector;
    private final CsProperties properties;

    public SemanticResponseCache(EmbeddingModel embeddingModel,
                                 SemanticCacheStore store,
                                 CacheLifecycleManager lifecycle,
                                 ResponseCache responseCache,
                                 MetricsCollector metricsCollector,
                                 CsProperties properties) {
        this.embeddingModel = embeddingModel;
        this.store = store;
        this.lifecycle = lifecycle;
        this.responseCache = responseCache;
        this.metricsCollector = metricsCollector;
        this.properties = properties;
    }

    public Optional<String> get(String question) {
        if (!responseCache.shouldCache(question)) {
            return Optional.empty();
        }
        try {
            float[] queryVector = embeddingModel.embed(question);
            Optional<SemanticCacheStore.CachedAnswer> matched =
                    store.knn(queryVector, properties.getCache().getSemanticThreshold());
            if (matched.isEmpty()) {
                metricsCollector.increment("semantic_cache_miss");
                return Optional.empty();
            }
            SemanticCacheStore.CachedAnswer answer = matched.get();
            if (!lifecycle.isAlive(answer.id())) {
                store.delete(answer.id());
                metricsCollector.increment("semantic_cache_expired");
                return Optional.empty();
            }
            int hits = lifecycle.hit(answer.id());
            metricsCollector.increment("semantic_cache_hit");
            log.info("[CS] 语义缓存命中 id={}, hits={}, 原问题={}, 当前问题={}",
                    answer.id(), hits, answer.question(), question);
            return Optional.of(answer.answer());
        } catch (Exception e) {
            // Embedding 不可用时不影响主流程，退化为精确缓存
            log.warn("[CS] 语义缓存查询失败，跳过：{}", e.getMessage());
            return Optional.empty();
        }
    }

    public void put(String question, String answer, String category) {
        if (answer == null || answer.isBlank() || !responseCache.shouldCache(question)) {
            return;
        }
        try {
            float[] vector = embeddingModel.embed(question);
            String cacheId = store.save(question, answer, vector, category);
            lifecycle.register(cacheId);
            metricsCollector.increment("semantic_cache_write");
        } catch (Exception e) {
            log.warn("[CS] 语义缓存写入失败，跳过：{}", e.getMessage());
        }
    }

    /** 知识库更新后主动失效 */
    public void invalidateAll() {
        store.clear();
        lifecycle.clear();
        log.info("[CS] 语义缓存已全部失效");
    }

    /** 定时回收 PG 侧冷数据（Redis 侧已过期的缓存） */
    public int evictCold() {
        int evicted = store.evictOlderThan(properties.getCache().getColdRetentionMillis());
        if (evicted > 0) {
            log.info("[CS] 回收冷语义缓存 {} 条", evicted);
        }
        return evicted;
    }

    public java.util.Map<String, Object> stats() {
        java.util.Map<String, Object> result = new java.util.LinkedHashMap<>();
        result.put("total", store.size());
        result.putAll(lifecycle.stats());
        result.put("threshold", properties.getCache().getSemanticThreshold());
        return result;
    }
}
