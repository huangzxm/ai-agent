package com.yupi.yuaiagent.cs.rag;

import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.vectorstore.SimpleVectorStore;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

/**
 * 客服知识库向量库（内存版，与恋爱大师知识库隔离）
 */
@Configuration
public class CsKnowledgeBaseConfig {

    @Bean("csVectorStore")
    public VectorStore csVectorStore(EmbeddingModel dashscopeEmbeddingModel,
                                     CsDocumentLoader csDocumentLoader) {
        SimpleVectorStore vectorStore = SimpleVectorStore.builder(dashscopeEmbeddingModel).build();
        List<Document> documents = csDocumentLoader.loadMarkdowns();
        if (!documents.isEmpty()) {
            vectorStore.add(documents);
        }
        return vectorStore;
    }
}
