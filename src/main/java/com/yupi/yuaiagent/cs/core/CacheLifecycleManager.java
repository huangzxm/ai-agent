package com.yupi.yuaiagent.cs.core;

import com.yupi.yuaiagent.cs.CsProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 缓存生命周期管理（对应生产架构中的 Redis 侧）：
 * 只负责 TTL 与原子计数，不负责语义检索。
 *
 * 全量暂存：新缓存写入短 TTL（默认 5 分钟）。
 * 热点晋升：命中次数达到阈值后，TTL 延长为长效（默认 24 小时）。
 */
@Component
@Slf4j
public class CacheLifecycleManager {

    private final CsProperties properties;

    private final Map<String, Meta> metas = new ConcurrentHashMap<>();

    public CacheLifecycleManager(CsProperties properties) {
        this.properties = properties;
    }

    /** 新缓存登记：短 TTL + 计数器归零 */
    public void register(String cacheId) {
        metas.put(cacheId, new Meta(expireAt(properties.getCache().getTtlSeconds()), new AtomicInteger(), false));
    }

    /**
     * 校验是否存活，过期则视为未命中并清理
     */
    public boolean isAlive(String cacheId) {
        Meta meta = metas.get(cacheId);
        if (meta == null) {
            return false;
        }
        if (System.currentTimeMillis() > meta.expireAt) {
            metas.remove(cacheId);
            return false;
        }
        return true;
    }

    /**
     * 命中计数 +1，并在达到阈值时做热点晋升
     */
    public int hit(String cacheId) {
        Meta meta = metas.get(cacheId);
        if (meta == null) {
            return 0;
        }
        int count = meta.hits.incrementAndGet();
        if (!meta.promoted && count >= properties.getCache().getPromoteThreshold()) {
            meta.promoted = true;
            meta.expireAt = expireAt(properties.getCache().getPromotedTtlSeconds());
            log.info("[CS] 语义缓存热点晋升 id={}, hits={}, 新TTL={}s",
                    cacheId, count, properties.getCache().getPromotedTtlSeconds());
        }
        return count;
    }

    public void evict(String cacheId) {
        metas.remove(cacheId);
    }

    public void clear() {
        metas.clear();
    }

    public Map<String, Object> stats() {
        long promoted = metas.values().stream().filter(m -> m.promoted).count();
        return Map.of("alive", metas.size(), "promoted", promoted);
    }

    private long expireAt(long ttlSeconds) {
        return System.currentTimeMillis() + ttlSeconds * 1000;
    }

    private static class Meta {
        private volatile long expireAt;
        private final AtomicInteger hits;
        private volatile boolean promoted;

        private Meta(long expireAt, AtomicInteger hits, boolean promoted) {
            this.expireAt = expireAt;
            this.hits = hits;
            this.promoted = promoted;
        }
    }
}
