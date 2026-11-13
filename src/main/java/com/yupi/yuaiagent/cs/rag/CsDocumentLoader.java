package com.yupi.yuaiagent.cs.rag;

import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.ai.reader.markdown.MarkdownDocumentReader;
import org.springframework.ai.reader.markdown.config.MarkdownDocumentReaderConfig;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.ResourcePatternResolver;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 客服知识库加载器：为每篇文档注入 category 等业务元数据，供检索时精准过滤
 */
@Component
@Slf4j
public class CsDocumentLoader {

    /** 文件名（不含扩展名）即业务分类 */
    private static final Map<String, String> CATEGORY_NAMES = Map.of(
            "refund", "退换货退款",
            "logistics", "物流配送",
            "invoice", "发票服务",
            "account", "账号安全",
            "order", "订单服务",
            "policy", "售后政策");

    private final ResourcePatternResolver resourcePatternResolver;

    public CsDocumentLoader(ResourcePatternResolver resourcePatternResolver) {
        this.resourcePatternResolver = resourcePatternResolver;
    }

    public List<Document> loadMarkdowns() {
        List<Document> allDocuments = new ArrayList<>();
        try {
            Resource[] resources = resourcePatternResolver.getResources("classpath:document/cs/*.md");
            for (Resource resource : resources) {
                String filename = resource.getFilename();
                String category = filename == null ? "unknown" : filename.replace(".md", "");
                MarkdownDocumentReaderConfig config = MarkdownDocumentReaderConfig.builder()
                        .withHorizontalRuleCreateDocument(true)
                        .withIncludeCodeBlock(false)
                        .withIncludeBlockquote(false)
                        .withAdditionalMetadata("filename", filename)
                        .withAdditionalMetadata("category", category)
                        .withAdditionalMetadata("categoryName", CATEGORY_NAMES.getOrDefault(category, category))
                        .build();
                MarkdownDocumentReader reader = new MarkdownDocumentReader(resource, config);
                allDocuments.addAll(reader.get());
            }
            log.info("[CS] 客服知识库加载完成，文档片段数={}", allDocuments.size());
        } catch (IOException e) {
            log.error("[CS] 客服知识库加载失败", e);
        }
        return allDocuments;
    }
}
