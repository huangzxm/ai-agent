package com.yupi.yuaiagent.cs.rag;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 轻量 BM25 关键词索引：弥补向量检索对订单号、专有名词、精确短语的召回不足。
 * 中文按 2-gram 切分，英文/数字按词切分，文档量级（数百片）下完全够用。
 */
@Component
@Slf4j
public class Bm25Index {

    private static final double K1 = 1.5;
    private static final double B = 0.75;

    private static final Pattern ASCII_WORD = Pattern.compile("[a-zA-Z0-9_]+");
    private static final Pattern CJK_RUN = Pattern.compile("[\\u4e00-\\u9fa5]+");

    private final CsDocumentLoader documentLoader;

    /** token → 倒排表 */
    private final Map<String, List<Posting>> inverted = new HashMap<>();
    private final Map<String, Document> documentById = new HashMap<>();
    private final Map<String, Integer> docLengths = new HashMap<>();

    private int docCount;
    private double avgDocLength = 1.0;
    private volatile boolean ready;

    public Bm25Index(CsDocumentLoader documentLoader) {
        this.documentLoader = documentLoader;
    }

    @PostConstruct
    public void init() {
        rebuild(documentLoader.loadMarkdowns());
    }

    public synchronized void rebuild(List<Document> documents) {
        inverted.clear();
        documentById.clear();
        docLengths.clear();
        long totalLength = 0;

        for (Document document : documents) {
            String id = document.getId() == null ? String.valueOf(document.getText().hashCode()) : document.getId();
            documentById.put(id, document);
            List<String> tokens = tokenize(document.getText());
            docLengths.put(id, tokens.size());
            totalLength += tokens.size();

            Map<String, Integer> termFrequency = new HashMap<>();
            for (String token : tokens) {
                termFrequency.merge(token, 1, Integer::sum);
            }
            termFrequency.forEach((token, tf) ->
                    inverted.computeIfAbsent(token, k -> new ArrayList<>()).add(new Posting(id, tf)));
        }

        docCount = documents.size();
        avgDocLength = docCount == 0 ? 1.0 : (double) totalLength / docCount;
        ready = true;
        log.info("[CS] BM25 索引构建完成：文档={}，词项={}", docCount, inverted.size());
    }

    /**
     * 关键词检索，支持按业务分类前置过滤
     */
    public List<Document> search(String query, int topK, String category) {
        if (!ready || docCount == 0 || query == null || query.isBlank()) {
            return List.of();
        }
        Map<String, Double> scores = new HashMap<>();
        for (String token : tokenize(query)) {
            List<Posting> postings = inverted.get(token);
            if (postings == null || postings.isEmpty()) {
                continue;
            }
            double idf = Math.log(1 + (docCount - postings.size() + 0.5) / (postings.size() + 0.5));
            for (Posting posting : postings) {
                int docLength = docLengths.getOrDefault(posting.docId, 1);
                double tfPart = posting.tf * (K1 + 1)
                        / (posting.tf + K1 * (1 - B + B * docLength / avgDocLength));
                scores.merge(posting.docId, idf * tfPart, Double::sum);
            }
        }
        return scores.entrySet().stream()
                .filter(e -> matchesCategory(e.getKey(), category))
                .sorted(Map.Entry.<String, Double>comparingByValue().reversed())
                .limit(topK)
                .map(e -> documentById.get(e.getKey()))
                .filter(java.util.Objects::nonNull)
                .toList();
    }

    private boolean matchesCategory(String docId, String category) {
        if (category == null || category.isBlank()) {
            return true;
        }
        Document document = documentById.get(docId);
        return document != null && category.equals(String.valueOf(document.getMetadata().get("category")));
    }

    /**
     * 中文 2-gram + 英文/数字整词
     */
    public List<String> tokenize(String text) {
        if (text == null || text.isBlank()) {
            return List.of();
        }
        Set<String> tokens = new LinkedHashSet<>();
        Matcher asciiMatcher = ASCII_WORD.matcher(text);
        while (asciiMatcher.find()) {
            tokens.add(asciiMatcher.group().toLowerCase());
        }
        Matcher cjkMatcher = CJK_RUN.matcher(text);
        while (cjkMatcher.find()) {
            String run = cjkMatcher.group();
            if (run.length() == 1) {
                continue;
            }
            for (int i = 0; i + 2 <= run.length(); i++) {
                tokens.add(run.substring(i, i + 2));
            }
        }
        return new ArrayList<>(tokens);
    }

    /** 供精排计算覆盖率使用 */
    public List<String> queryTokens(String query) {
        return sortByLengthDesc(tokenize(query));
    }

    private List<String> sortByLengthDesc(List<String> tokens) {
        List<String> copy = new ArrayList<>(tokens);
        copy.sort(Comparator.comparingInt(String::length).reversed());
        return copy;
    }

    private record Posting(String docId, int tf) {
    }
}
