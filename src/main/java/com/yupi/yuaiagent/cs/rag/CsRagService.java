package com.yupi.yuaiagent.cs.rag;

import com.yupi.yuaiagent.cs.CsProperties;
import com.yupi.yuaiagent.cs.support.CriticalServiceDownException;
import com.yupi.yuaiagent.cs.support.ServiceCircuitBreaker;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.filter.FilterExpressionBuilder;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 客服 RAG 检索链路（混合检索 + 精排）：
 * 1) 查询重写：指代消解 + 润色 + 术语标准化（带缓存）
 * 2) 前置过滤：按 category 等业务元数据缩小检索范围
 * 3) 双路召回：向量（语义） + BM25（关键词）
 * 4) RRF 粗排融合：按名次加权合并，宁可多召不漏召
 * 5) 精排：Cross-Encoder 思路重打分，取 Top-K 生成答案
 * 6) 置信度不足拒答转人工；检索为空给兜底引导不降级
 */
@Component
@Slf4j
public class CsRagService {

    private static final String VECTOR_SERVICE = "vectorStore";

    private static final String ANSWER_PROMPT = """
            你是电商平台的智能客服。请严格依据下面的「参考资料」回答用户问题。
            要求：
            1. 只使用参考资料中的信息，不要编造政策、时效或金额。
            2. 若参考资料不足以回答，直接回答「这个问题我需要为您转接人工客服进一步核实」。
            3. 回答简洁分点，不超过 200 字，语气礼貌。
            4. 结合「对话上下文」理解用户的指代和省略（如「它」「这个」「那运费呢」），
               但事实依据仍只能来自参考资料；上下文与资料冲突时以资料为准。
            """;

    private final VectorStore csVectorStore;
    private final QueryRewritePipeline rewritePipeline;
    private final Bm25Index bm25Index;
    private final RrfFusion rrfFusion;
    private final Reranker reranker;
    private final ChatClient chatClient;
    private final CsProperties properties;
    private final ServiceCircuitBreaker circuitBreaker;

    public CsRagService(@Qualifier("csVectorStore") VectorStore csVectorStore,
                        QueryRewritePipeline rewritePipeline,
                        Bm25Index bm25Index,
                        RrfFusion rrfFusion,
                        Reranker reranker,
                        ChatModel dashscopeChatModel,
                        CsProperties properties,
                        ServiceCircuitBreaker circuitBreaker) {
        this.csVectorStore = csVectorStore;
        this.rewritePipeline = rewritePipeline;
        this.bm25Index = bm25Index;
        this.rrfFusion = rrfFusion;
        this.reranker = reranker;
        this.chatClient = ChatClient.builder(dashscopeChatModel).build();
        this.properties = properties;
        this.circuitBreaker = circuitBreaker;
    }

    public RagAnswer answer(String question, String category, List<Message> history) {
        String historySummary = summarizeHistory(history);
        String rewrittenQuery = rewritePipeline.rewrite(question, historySummary);

        // 1. 前置过滤 + 双路召回 + RRF 融合
        List<Document> candidates = retrieve(rewrittenQuery, category);
        if (candidates.isEmpty() && category != null) {
            log.info("[CS] 分类 {} 无命中，退化为全库检索", category);
            candidates = retrieve(rewrittenQuery, null);
        }

        if (candidates.isEmpty()) {
            log.info("[CS] 未检索到相关文档，返回兜底话术（不降级）");
            return new RagAnswer("很抱歉，我没有找到相关信息。您可以换个说法描述问题，"
                    + "或者回复「转人工」由人工客服帮您处理。", 0.0, List.of(), false);
        }

        // 2. 精排：对候选集深度打分
        List<Reranker.RerankedDocument> ranked = reranker.rerank(rewrittenQuery, candidates);
        if (ranked.isEmpty()) {
            return RagAnswer.lowConfidence(0.0);
        }
        double bestScore = ranked.stream().mapToDouble(Reranker.RerankedDocument::score).max().orElse(0.0);
        double threshold = properties.getRag().getSimilarityThreshold();
        if (bestScore < threshold) {
            log.info("[CS] 精排置信度不足 bestScore={} threshold={}", bestScore, threshold);
            return RagAnswer.lowConfidence(bestScore);
        }

        // 3. 取 Top-K 生成答案
        List<Reranker.RerankedDocument> topDocuments = ranked.stream()
                .limit(properties.getRag().getTopK())
                .toList();
        String context = topDocuments.stream()
                .map(item -> item.document().getText())
                .collect(Collectors.joining("\n\n---\n\n"));
        List<String> sources = topDocuments.stream()
                .map(item -> String.valueOf(item.document().getMetadata().getOrDefault("categoryName", "未知")))
                .distinct()
                .toList();

        try {
            String answer = chatClient.prompt()
                    .system(ANSWER_PROMPT)
                    .user(u -> u.text("对话上下文：\n{history}\n\n参考资料：\n{context}\n\n用户问题：{question}")
                            .param("history", historySummary.isBlank() ? "无" : historySummary)
                            .param("context", context)
                            .param("question", question))
                    .call()
                    .content();
            log.info("[CS] RAG 检索完成：重写={}, 候选={}, 精排Top={}, 来源={}, 置信度={}",
                    rewrittenQuery, candidates.size(), topDocuments.size(), sources, bestScore);
            return new RagAnswer(answer == null ? "" : answer.trim(), bestScore, sources, false);
        } catch (Exception e) {
            log.error("[CS] RAG 答案生成失败：{}", e.getMessage());
            return RagAnswer.lowConfidence(bestScore);
        }
    }

    /**
     * 双路召回 + RRF 粗排融合
     */
    private List<Document> retrieve(String query, String category) {
        int candidateTopN = properties.getRag().getHybrid().getCandidateTopN();
        List<Document> vectorDocs = vectorSearch(query, category, candidateTopN);
        List<Document> bm25Docs = bm25Index.search(query, candidateTopN, category);
        return rrfFusion.fuse(vectorDocs, bm25Docs);
    }

    private List<Document> vectorSearch(String query, String category, int topK) {
        // 向量库属关键基础设施：熔断打开时直接转人工，不让模型盲答
        if (!circuitBreaker.allow(VECTOR_SERVICE)) {
            throw new CriticalServiceDownException(VECTOR_SERVICE, "知识库服务暂时不可用（熔断中）");
        }
        SearchRequest.Builder builder = SearchRequest.builder()
                .query(query)
                .topK(topK)
                // 粗排阶段不设相似度下限，交给 RRF/精排判断
                .similarityThreshold(0.0);
        if (category != null && !category.isBlank()) {
            builder.filterExpression(new FilterExpressionBuilder().eq("category", category).build());
        }
        try {
            List<Document> documents = csVectorStore.similaritySearch(builder.build());
            circuitBreaker.recordSuccess(VECTOR_SERVICE);
            return documents == null ? new ArrayList<>() : documents;
        } catch (Exception e) {
            circuitBreaker.recordFailure(VECTOR_SERVICE);
            log.error("[CS] 向量检索失败：{}", e.getMessage());
            throw new CriticalServiceDownException(VECTOR_SERVICE, "知识库检索失败：" + e.getMessage(), e);
        }
    }

    /**
     * 检索链路调试：展示重写、双路召回、RRF 融合、精排各阶段结果，便于验证与调参
     */
    public RetrievalDebug debugRetrieval(String query, String category) {
        String rewritten = rewritePipeline.rewrite(query, "");
        int topN = properties.getRag().getHybrid().getCandidateTopN();
        List<Document> vectorDocs = vectorSearch(rewritten, category, topN);
        List<Document> bm25Docs = bm25Index.search(rewritten, topN, category);
        List<Document> fused = rrfFusion.fuse(vectorDocs, bm25Docs);
        List<Reranker.RerankedDocument> ranked = reranker.rerank(rewritten, fused);

        List<Hit> vectorHits = vectorDocs.stream()
                .map(d -> new Hit(categoryOf(d), snippetOf(d), scoreOf(d)))
                .toList();
        List<Hit> bm25Hits = bm25Docs.stream()
                .map(d -> new Hit(categoryOf(d), snippetOf(d), 0.0))
                .toList();
        List<Hit> fusedHits = fused.stream()
                .map(d -> new Hit(categoryOf(d), snippetOf(d), scoreOf(d)))
                .toList();
        List<Hit> rerankedHits = ranked.stream()
                .map(r -> new Hit(categoryOf(r.document()), snippetOf(r.document()), r.score()))
                .toList();
        double confidence = ranked.stream()
                .mapToDouble(Reranker.RerankedDocument::score)
                .max()
                .orElse(0.0);
        return new RetrievalDebug(query, rewritten, vectorHits, bm25Hits, fusedHits, rerankedHits, confidence);
    }

    private String categoryOf(Document document) {
        return String.valueOf(document.getMetadata().getOrDefault("categoryName", "未知"));
    }

    private String snippetOf(Document document) {
        String text = document.getText() == null ? "" : document.getText().replaceAll("\\s+", " ").trim();
        return text.length() <= 80 ? text : text.substring(0, 80) + "…";
    }

    private double scoreOf(Document document) {
        Double score = document.getScore();
        if (score != null) {
            return score;
        }
        Object distance = document.getMetadata().get("distance");
        return distance instanceof Number number ? 1 - number.doubleValue() : 0.0;
    }

    public record Hit(String category, String snippet, double score) {
    }

    public record RetrievalDebug(String originalQuery, String rewrittenQuery,
                                 List<Hit> vectorHits, List<Hit> bm25Hits,
                                 List<Hit> fusedHits, List<Hit> rerankedHits,
                                 double confidence) {
    }

    private String summarizeHistory(List<Message> history) {
        if (history == null || history.isEmpty()) {
            return "";
        }
        return history.stream()
                .filter(m -> m.getText() != null && !m.getText().isBlank())
                .skip(Math.max(0, history.size() - 4))
                .map(m -> m.getMessageType().name() + "：" + m.getText())
                .collect(Collectors.joining("\n"));
    }
}
