package com.yupi.yuaiagent.cs.rag;

import com.yupi.yuaiagent.cs.CsProperties;
import org.springframework.ai.document.Document;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 加权 RRF（Reciprocal Rank Fusion）融合：
 * 向量与 BM25 评分量纲不同，按名次而非原始分数融合，避免某一路分数尺度主导结果。
 * 粗排目标是「宁可多召，不可漏掉」，为后续精排提供足够候选。
 */
@Component
public class RrfFusion {

    private final CsProperties properties;

    public RrfFusion(CsProperties properties) {
        this.properties = properties;
    }

    public List<Document> fuse(List<Document> vectorDocs, List<Document> bm25Docs) {
        CsProperties.Hybrid config = properties.getRag().getHybrid();
        Map<String, Fused> fused = new HashMap<>();

        accumulate(fused, vectorDocs, config.getVectorWeight(), config.getRrfK());
        accumulate(fused, bm25Docs, config.getBm25Weight(), config.getRrfK());

        List<Fused> ordered = new ArrayList<>(fused.values());
        ordered.sort(Comparator.comparingDouble(Fused::score).reversed());
        int limit = Math.max(1, config.getCandidateTopN());
        return ordered.stream().limit(limit).map(Fused::document).toList();
    }

    private void accumulate(Map<String, Fused> fused, List<Document> documents, double weight, int k) {
        if (documents == null) {
            return;
        }
        for (int rank = 0; rank < documents.size(); rank++) {
            Document document = documents.get(rank);
            String key = keyOf(document);
            final int rankValue = rank + 1;
            final double gain = weight * (1.0 / (k + rankValue));
            fused.compute(key, (id, existing) -> {
                if (existing == null) {
                    return new Fused(document, gain, rankValue);
                }
                return existing.withScore(existing.score + gain);
            });
        }
    }

    private String keyOf(Document document) {
        return document.getId() == null ? String.valueOf(document.getText().hashCode()) : document.getId();
    }

    private record Fused(Document document, double score, int firstRank) {
        private Fused withScore(double newScore) {
            return new Fused(document, newScore, firstRank);
        }
    }
}
