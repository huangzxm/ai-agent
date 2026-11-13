package com.yupi.yuaiagent.cs.rag;

import java.util.List;

/**
 * RAG 回答结果
 *
 * @param answer     生成的答案，needHuman 时为空
 * @param confidence 检索最高相似度
 * @param sources    命中的文档分类
 * @param needHuman  置信度不足，需要转人工
 */
public record RagAnswer(String answer, double confidence, List<String> sources, boolean needHuman) {

    public static RagAnswer lowConfidence(double confidence) {
        return new RagAnswer("", confidence, List.of(), true);
    }
}
