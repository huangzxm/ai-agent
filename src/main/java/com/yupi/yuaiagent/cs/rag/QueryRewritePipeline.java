package com.yupi.yuaiagent.cs.rag;

import cn.hutool.crypto.digest.DigestUtil;
import com.yupi.yuaiagent.cs.CsProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 查询重写流水线：指代消解 + 口语润色 + 业务实体标准化。
 * 相同「历史 + 原问题」的重写结果可复用，缓存避免重复调用 LLM。
 */
@Component
@Slf4j
public class QueryRewritePipeline {

    private static final String REWRITE_PROMPT = """
            你是客服检索系统的查询重写器。根据对话历史，把用户最新问题改写成一条独立、完整、适合检索的书面问题。
            规则：
            1. 补全指代与省略（如「它」「这个」「那运费呢」），不改变用户意图；
            2. 去掉「那个」「咋整」「呃」等口语废话，改成书面表达；
            3. 保留订单号、单号、金额等实体原样，不得编造；
            4. 如果原问题已经完整，原样返回；
            5. 只输出一行重写后的问题，不要解释、不要引号。
            """;

    private final ChatClient chatClient;
    private final CsProperties properties;

    /** LRU 缓存：key = 历史摘要 + 原问题 */
    private final Map<String, String> cache;

    public QueryRewritePipeline(ChatModel dashscopeChatModel, CsProperties properties) {
        this.chatClient = ChatClient.builder(dashscopeChatModel).build();
        this.properties = properties;
        int maxSize = properties.getRewrite().getMaxCacheSize();
        this.cache = new LinkedHashMap<>(maxSize, 0.75f, true) {
            @Override
            protected boolean removeEldestEntry(Map.Entry<String, String> eldest) {
                return size() > maxSize;
            }
        };
    }

    public String rewrite(String query, String historySummary) {
        if (query == null || query.isBlank() || !properties.getRewrite().isEnabled()) {
            return query;
        }
        // 1. 业务术语标准化（代码层兜底，保证检索词与知识库一致）
        String normalized = normalizeSynonyms(query);

        // 2. 缓存命中直接复用
        String cacheKey = cacheKey(normalized, historySummary);
        synchronized (cache) {
            String cached = cache.get(cacheKey);
            if (cached != null) {
                log.info("[CS] 查询重写命中缓存：{} -> {}", query, cached);
                return cached;
            }
        }

        // 3. LLM 指代消解 + 润色
        String rewritten = normalized;
        if (historySummary != null && !historySummary.isBlank()) {
            rewritten = llmRewrite(normalized, historySummary);
        }
        // 4. 重写结果再做一次术语标准化
        rewritten = normalizeSynonyms(rewritten);

        synchronized (cache) {
            cache.put(cacheKey, rewritten);
        }
        return rewritten;
    }

    private String llmRewrite(String query, String historySummary) {
        try {
            String result = chatClient.prompt()
                    .system(REWRITE_PROMPT)
                    .user(u -> u.text("对话历史：\n{history}\n\n当前问题：{query}")
                            .param("history", historySummary)
                            .param("query", query))
                    .call()
                    .content();
            if (result == null || result.isBlank() || result.length() > query.length() * 4 + 20) {
                return query;
            }
            String cleaned = result.trim().replaceAll("^[\"'「『]|[\"'」』]$", "");
            log.info("[CS] 查询重写：{} -> {}", query, cleaned);
            return cleaned;
        } catch (Exception e) {
            log.warn("[CS] 查询重写失败，使用标准化后的原问句：{}", e.getMessage());
            return query;
        }
    }

    private String normalizeSynonyms(String text) {
        if (text == null || text.isBlank() || properties.getRewrite().getSynonyms().isEmpty()) {
            return text;
        }
        String result = text;
        for (Map.Entry<String, String> entry : properties.getRewrite().getSynonyms().entrySet()) {
            if (!entry.getKey().equals(entry.getValue())) {
                result = result.replace(entry.getKey(), entry.getValue());
            }
        }
        return result;
    }

    private String cacheKey(String query, String historySummary) {
        return DigestUtil.md5Hex((historySummary == null ? "" : historySummary) + "|" + query);
    }

    public int cacheSize() {
        synchronized (cache) {
            return cache.size();
        }
    }
}
