package com.yupi.yuaiagent.cs.core;

import com.yupi.yuaiagent.cs.CsProperties;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;
import java.util.regex.Pattern;

/**
 * 语义缓存（工程简化版）：问题归一化 + TTL + LRU + 热点晋升。
 *
 * 策略：
 * 1. 未命中的问题在 Agent/RAG 处理完后「全量暂存」，TTL 较短（默认 5 分钟）；
 * 2. 再次命中同一问题计数 +1，达到阈值后晋升为长效缓存（默认 24 小时）；
 * 3. 含订单号/手机号等个性化问题不缓存，避免跨用户串答案。
 */
@Component
@Slf4j
public class ResponseCache {

    /** 含 4 位以上数字（订单号/手机号等）视为个性化问题，不缓存 */
    private static final Pattern SENSITIVE = Pattern.compile(
            "(?<![0-9A-Za-z])([A-Za-z]{0,2}\\d{4,}|1[3-9]\\d{9})(?![0-9A-Za-z])");

    private final CsProperties properties;

    private final Map<String, CacheEntry> store;

    private final AtomicLong hits = new AtomicLong();
    private final AtomicLong misses = new AtomicLong();
    private final AtomicLong promotions = new AtomicLong();

    public ResponseCache(CsProperties properties) {
        this.properties = properties;
        int maxSize = properties.getCache().getMaxSize();
        // accessOrder=true 实现 LRU
        this.store = new LinkedHashMap<>(maxSize, 0.75f, true) {
            @Override
            protected boolean removeEldestEntry(Map.Entry<String, CacheEntry> eldest) {
                return size() > maxSize;
            }
        };
    }

    @PostConstruct
    public void warmUp() {
        long longTtl = properties.getCache().getPromotedTtlSeconds();
        put("你好", "您好，我是智能客服小助手，可以帮您查询订单物流、办理退换货、解答售后政策。请问有什么可以帮您？", longTtl, true);
        put("您好", "您好，我是智能客服小助手，可以帮您查询订单物流、办理退换货、解答售后政策。请问有什么可以帮您？", longTtl, true);
        put("在吗", "在的，请告诉我您的问题，我马上为您处理。", longTtl, true);
        put("谢谢", "不客气，很高兴为您服务。还有其他问题随时找我。", longTtl, true);
        put("再见", "感谢您的咨询，祝您生活愉快，再见！", longTtl, true);
        log.info("[CS] FAQ 缓存预热完成，当前条目={}", store.size());
    }

    public synchronized Optional<String> get(String question) {
        String key = normalize(question);
        if (key.isEmpty()) {
            return Optional.empty();
        }
        CacheEntry entry = store.get(key);
        long now = System.currentTimeMillis();
        if (entry == null || now > entry.expireAt) {
            if (entry != null) {
                store.remove(key);
            }
            misses.incrementAndGet();
            return Optional.empty();
        }
        hits.incrementAndGet();
        // 热点晋升：命中达到阈值后延长 TTL
        if (!entry.promoted && entry.hits.incrementAndGet() >= properties.getCache().getPromoteThreshold()) {
            entry.promoted = true;
            entry.expireAt = now + properties.getCache().getPromotedTtlSeconds() * 1000;
            promotions.incrementAndGet();
            log.info("[CS] 热点问题晋升成功 key={}, hits={}, 新TTL={}s",
                    key, entry.hits.get(), properties.getCache().getPromotedTtlSeconds());
        }
        log.info("[CS] 命中FAQ缓存 key={}, promoted={}", key, entry.promoted);
        return Optional.of(entry.answer);
    }

    /**
     * 全量暂存：Agent/RAG 成功处理后调用，使用短 TTL
     */
    public void put(String question, String answer) {
        put(question, answer, properties.getCache().getTtlSeconds(), false);
    }

    public synchronized void put(String question, String answer, long ttlSeconds, boolean promoted) {
        if (answer == null || answer.isBlank() || !shouldCache(question)) {
            return;
        }
        String key = normalize(question);
        if (key.isEmpty()) {
            return;
        }
        store.put(key, new CacheEntry(answer, System.currentTimeMillis() + ttlSeconds * 1000, promoted));
    }

    /**
     * 个性化问题（含订单号等）不缓存，防止跨用户串答案
     */
    public boolean shouldCache(String question) {
        if (question == null) {
            return false;
        }
        String trimmed = question.trim();
        return trimmed.length() >= 2 && trimmed.length() <= 60 && !SENSITIVE.matcher(trimmed).find();
    }

    /** 归一化：去空白/标点/语气词，大小写统一，提升相似问法命中率 */
    public String normalize(String question) {
        if (question == null) {
            return "";
        }
        return question.trim().toLowerCase()
                .replaceAll("[\\p{Punct}\\pP~`！@#￥%…&*（）——、，。？！；：‘’“”\\s]", "")
                .replaceAll("(请问|一下|呢|啊|呀|吧|哦|哈)$", "");
    }

    public Map<String, Long> stats() {
        return Map.of("hits", hits.get(), "misses", misses.get(),
                "size", (long) store.size(), "promotions", promotions.get());
    }

    private static class CacheEntry {
        private final String answer;
        private final AtomicLong hits = new AtomicLong();
        private volatile long expireAt;
        private volatile boolean promoted;

        private CacheEntry(String answer, long expireAt, boolean promoted) {
            this.answer = answer;
            this.expireAt = expireAt;
            this.promoted = promoted;
        }
    }
}
