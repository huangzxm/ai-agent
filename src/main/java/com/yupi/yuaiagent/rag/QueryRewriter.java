package com.yupi.yuaiagent.rag;

import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.rag.Query;
import org.springframework.ai.rag.preretrieval.query.transformation.QueryTransformer;
import org.springframework.ai.rag.preretrieval.query.transformation.RewriteQueryTransformer;
import org.springframework.stereotype.Component;

/**
 * 查询重写器：支持通用重写与「带历史的指代消解重写」
 */
@Component
@Slf4j
public class QueryRewriter {

    private static final String CONTEXT_REWRITE_PROMPT = """
            你是一个查询重写助手。根据给定的对话历史，把用户的最新问题重写为一个独立、完整、可检索的问题。
            约束：
            1. 只补全指代（如「他」「它」「这个订单」）和省略的主语，不改变用户原始意图；
            2. 不要臆造新条件，不要扩写用户没说的内容；
            3. 如果原问题本身已经完整，原样返回；
            4. 只输出重写后的问题，不要输出解释或引号。
            """;

    private final QueryTransformer queryTransformer;

    private final ChatClient chatClient;

    public QueryRewriter(ChatModel dashscopeChatModel) {
        // 分别构建，避免共用 Builder 带来的状态问题
        this.queryTransformer = RewriteQueryTransformer.builder()
                .chatClientBuilder(ChatClient.builder(dashscopeChatModel))
                .build();
        this.chatClient = ChatClient.builder(dashscopeChatModel).build();
    }

    /**
     * 执行查询重写（无上下文，兼容旧调用）
     */
    public String doQueryRewrite(String prompt) {
        Query query = new Query(prompt);
        Query transformedQuery = queryTransformer.transform(query);
        return transformedQuery.text();
    }

    /**
     * 带对话历史的指代消解重写，失败时回退原问句（双保险：原始 Query 也会参与召回）
     */
    public String doContextRewrite(String query, String historySummary) {
        if (query == null || query.isBlank()) {
            return query;
        }
        if (historySummary == null || historySummary.isBlank()) {
            return query;
        }
        try {
            String rewritten = chatClient.prompt()
                    .system(CONTEXT_REWRITE_PROMPT)
                    .user(u -> u.text("对话历史：\n{history}\n\n当前问题：{query}")
                            .param("history", historySummary)
                            .param("query", query))
                    .call()
                    .content();
            if (rewritten == null || rewritten.isBlank() || rewritten.length() > query.length() * 4) {
                return query;
            }
            log.info("[CS] Query 重写：{} -> {}", query, rewritten.trim());
            return rewritten.trim();
        } catch (Exception e) {
            log.warn("[CS] 上下文重写失败，使用原问句：{}", e.getMessage());
            return query;
        }
    }
}
