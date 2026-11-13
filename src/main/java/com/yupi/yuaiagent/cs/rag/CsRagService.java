package com.yupi.yuaiagent.cs.rag;

import com.yupi.yuaiagent.cs.CsProperties;
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

import java.util.List;
import java.util.stream.Collectors;

/**
 * 客服 RAG 链路：Query 重写 → 向量检索（阈值 + 业务元数据过滤）→ 上下文增强 → 答案生成 → 置信度评估
 */
@Component
@Slf4j
public class CsRagService {

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

    public CsRagService(@Qualifier("csVectorStore") VectorStore csVectorStore,
                        QueryRewriter queryRewriter,
                        ChatModel dashscopeChatModel,
                        CsProperties properties) {
        this.csVectorStore = csVectorStore;
        this.queryRewriter = queryRewriter;
        this.chatClient = ChatClient.builder(dashscopeChatModel).build();
        this.properties = properties;
    }

    /**
     * 执行 RAG 问答
     *
     * @param question 用户原始问题
     * @param category 路由给出的业务分类，可为空
     * @param history  裁剪后的历史消息
     */
    public RagAnswer answer(String question, String category, List<Message> history) {
        String query = rewriteQuery(question);
        double threshold = properties.getRag().getSimilarityThreshold();

        // 1. 带业务元数据过滤的检索
        List<Document> documents = search(query, category);
        if (documents.isEmpty() && category != null) {
            // 分类过滤过严时退化为全库检索，避免误伤
            log.info("[CS] 分类 {} 无命中，退化为全库检索", category);
            documents = search(query, null);
        }

        // 2. 置信度评估：最高相似度不足直接转人工，不硬编答案
        double bestScore = documents.stream()
                .mapToDouble(this::scoreOf)
                .max()
                .orElse(0.0);
        if (documents.isEmpty() || bestScore < threshold) {
            log.info("[CS] RAG 置信度不足 bestScore={} threshold={}", bestScore, threshold);
            return RagAnswer.lowConfidence(bestScore);
        }

        // 3. 上下文增强 + 生成
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
            log.info("[CS] RAG 命中分类={} bestScore={}", sources, bestScore);
            return new RagAnswer(answer == null ? "" : answer.trim(), bestScore, sources, false);
        } catch (Exception e) {
            log.error("[CS] RAG 答案生成失败：{}", e.getMessage());
            return RagAnswer.lowConfidence(bestScore);
        }
    }

    private List<Document> search(String query, String category) {
        SearchRequest.Builder builder = SearchRequest.builder()
                .query(query)
                .topK(properties.getRag().getTopK())
                .similarityThreshold(properties.getRag().getSimilarityThreshold());
        if (category != null && !category.isBlank()) {
            builder.filterExpression(new FilterExpressionBuilder().eq("category", category).build());
        }
        try {
            return csVectorStore.similaritySearch(builder.build());
        } catch (Exception e) {
            log.warn("[CS] 向量检索失败：{}", e.getMessage());
            return List.of();
        }
    }

    private String rewriteQuery(String question) {
        try {
            String rewritten = queryRewriter.doQueryRewrite(question);
            return rewritten == null || rewritten.isBlank() ? question : rewritten;
        } catch (Exception e) {
            log.warn("[CS] Query 重写失败，使用原问句：{}", e.getMessage());
            return question;
        }
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
