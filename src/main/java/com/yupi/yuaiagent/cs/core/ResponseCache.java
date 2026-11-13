package com.yupi.yuaiagent.cs.core;

import com.yupi.yuaiagent.cs.CsProperties;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 高频问题缓存：问题归一化 + TTL + LRU，命中直接返回，跳过 LLM
 */
@Component
@Slf4j
public class ResponseCache {

    private final CsProperties properties;

    private final Map<String, CacheEntry> store;

    private final AtomicLong hits = new AtomicLong();
    private final AtomicLong misses = new AtomicLong();

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
        // 预置高频寒暄，冷启动即可命中
        put("你好", "您好，我是智能客服小助手，可以帮您查询订单物流、办理退换货、解答售后政策。请问有什么可以帮您？");
        put("您好", "您好，我是智能客服小助手，可以帮您查询订单物流、办理退换货、解答售后政策。请问有什么可以帮您？");
        put("在吗", "在的，请告诉我您的问题，我马上为您处理。");
        put("谢谢", "不客气，很高兴为您服务。还有其他问题随时找我。");
        put("再见", "感谢您的咨询，祝您生活愉快，再见！");
        log.info("[CS] FAQ 缓存预热完成，当前条目={}", store.size());
    }

    public synchronized Optional<String> get(String question) {
        String key = normalize(question);
        if (key.isEmpty()) {
            return Optional.empty();
        }
        CacheEntry entry = store.get(key);
        long now = System.currentTimeMillis();
        if (entry == null || now - entry.createdAt > properties.getCache().getTtlSeconds() * 1000) {
            if (entry != null) {
                store.remove(key);
            }
            misses.incrementAndGet();
            return Optional.empty();
        }
        hits.incrementAndGet();
        log.info("[CS] 命中FAQ缓存 key={}", key);
        return Optional.of(entry.answer);
    }

    public synchronized void put(String question, String answer) {
        String key = normalize(question);
        if (key.isEmpty()) {
            return;
        }
        store.put(key, new CacheEntry(answer, System.currentTimeMillis()));
    }

    /** 归一化：去空白/标点/语气词，大小写统一，提升相似问法命中率 */
    public String normalize(String question) {
        if (question == null) {
            return "";
        }
        String normalized = question.trim().toLowerCase()
                .replaceAll("[\\p{Punct}\\pP~`！@#￥%…&*（）——、，。？！；：‘’“”\\s]", "")
                .replaceAll("(请问|一下|呢|啊|呀|吧|哦|哈)$", "");
        return normalized;
    }

    public Map<String, Long> stats() {
        return Map.of("hits", hits.get(), "misses", misses.get(), "size", (long) store.size());
    }

    private record CacheEntry(String answer, long createdAt) {
    }
}
