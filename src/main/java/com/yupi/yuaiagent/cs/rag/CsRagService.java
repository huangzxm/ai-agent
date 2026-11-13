package com.yupi.yuaiagent.cs.rag;

import com.yupi.yuaiagent.cs.CsProperties;
import com.yupi.yuaiagent.cs.support.CriticalServiceDownException;
import com.yupi.yuaiagent.cs.support.ServiceCircuitBreaker;
import com.yupi.yuaiagent.rag.QueryRewriter;
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
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 客服 RAG 链路：
 * 1) 带历史的指代消解重写（不改变意图，失败回退原问句）
 * 2) 原始 Query 与重写 Query 双路召回去重合并（简化版混合召回，避免重写跑偏）
 * 3) 相似度阈值 + 业务元数据过滤
 * 4) 置信度不足直接拒答转人工，不让模型基于低质量上下文编造
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
            """;

    private final VectorStore csVectorStore;
    private final QueryRewriter queryRewriter;
    private final ChatClient chatClient;
    private final CsProperties properties;
    private final ServiceCircuitBreaker circuitBreaker;

    public CsRagService(@Qualifier("csVectorStore") VectorStore csVectorStore,
                        QueryRewriter queryRewriter,
                        ChatModel dashscopeChatModel,
                        CsProperties properties,
                        ServiceCircuitBreaker circuitBreaker) {
        this.csVectorStore = csVectorStore;
        this.queryRewriter = queryRewriter;
        this.chatClient = ChatClient.builder(dashscopeChatModel).build();
        this.properties = properties;
        this.circuitBreaker = circuitBreaker;
    }

    public RagAnswer answer(String question, String category, List<Message> history) {
        String historySummary = summarizeHistory(history);
        String rewritten = queryRewriter.doContextRewrite(question, historySummary);

        // 双路召回：重写 Query + 原始 Query
        List<String> queries = rewritten.equals(question) ? List.of(question) : List.of(rewritten, question);
        double threshold = properties.getRag().getSimilarityThreshold();

        List<Document> documents = searchMerged(queries, category);
        if (documents.isEmpty() && category != null) {
            log.info("[CS] 分类 {} 无命中，退化为全库检索", category);
            documents = searchMerged(queries, null);
        }

        // 检索为空属于正常业务：给兜底引导话术，不直接转人工
        if (documents.isEmpty()) {
            log.info("[CS] RAG 未检索到相关文档，返回兜底话术（不降级）");
            return new RagAnswer("很抱歉，我没有找到相关信息。您可以换个说法描述问题，"
                    + "或者回复「转人工」由人工客服帮您处理。", 0.0, List.of(), false);
        }

        // 检索到了但置信度不足：业务上不允许自动回答，转人工
        double bestScore = documents.stream().mapToDouble(this::scoreOf).max().orElse(0.0);
        if (bestScore < threshold) {
            log.info("[CS] RAG 置信度不足 bestScore={} threshold={}", bestScore, threshold);
            return RagAnswer.lowConfidence(bestScore);
        }

        String context = documents.stream()
                .map(Document::getText)
                .collect(Collectors.joining("\n\n---\n\n"));
        List<String> sources = documents.stream()
                .map(d -> String.valueOf(d.getMetadata().getOrDefault("categoryName", "未知")))
                .distinct()
                .toList();
        try {
            String answer = chatClient.prompt()
                    .system(ANSWER_PROMPT)
                    .user(u -> u.text("参考资料：\n{context}\n\n用户问题：{question}")
                            .param("context", context)
                            .param("question", question))
                    .call()
                    .content();
            log.info("[CS] RAG 命中分类={} bestScore={} 召回路数={}", sources, bestScore, queries.size());
            return new RagAnswer(answer == null ? "" : answer.trim(), bestScore, sources, false);
        } catch (Exception e) {
            log.error("[CS] RAG 答案生成失败：{}", e.getMessage());
            return RagAnswer.lowConfidence(bestScore);
        }
    }

    /**
     * 多路召回 + 按文档去重（保留最高分）+ 取 TopK
     */
    private List<Document> searchMerged(List<String> queries, String category) {
        Map<String, Document> merged = new LinkedHashMap<>();
        for (String query : queries) {
            for (Document document : search(query, category)) {
                String key = document.getId() == null ? document.getText() : document.getId();
                Document existing = merged.get(key);
                if (existing == null || scoreOf(document) > scoreOf(existing)) {
                    merged.put(key, document);
                }
            }
        }
        return merged.values().stream()
                .sorted(Comparator.comparingDouble(this::scoreOf).reversed())
                .limit(properties.getRag().getTopK())
                .collect(Collectors.toList());
    }

    private List<Document> search(String query, String category) {
        // 向量库属关键基础设施：熔断打开时直接转人工，不让模型盲答
        if (!circuitBreaker.allow(VECTOR_SERVICE)) {
            throw new CriticalServiceDownException(VECTOR_SERVICE, "知识库服务暂时不可用（熔断中）");
        }
        SearchRequest.Builder builder = SearchRequest.builder()
                .query(query)
                .topK(properties.getRag().getTopK())
                .similarityThreshold(properties.getRag().getSimilarityThreshold());
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

    private double scoreOf(Document document) {
        Double score = document.getScore();
        if (score != null) {
            return score;
        }
        Object distance = document.getMetadata().get("distance");
        return distance instanceof Number number ? 1 - number.doubleValue() : 0.0;
    }
}
