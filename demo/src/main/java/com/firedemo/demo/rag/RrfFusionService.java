package com.firedemo.demo.rag;

import com.firedemo.demo.Entity.DocumentChunk;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * RRF（Reciprocal Rank Fusion）多路检索融合服务
 * <p>
 * 公式：RRF_score(chunk) = Σ 1 / (k + rank_i)
 * 其中 k 为平滑常数（默认 60），rank_i 为 chunk 在第 i 路检索中的排名（从 1 开始）。
 * <p>
 * 优势：不需要对各路检索分数做归一化，只关心排名顺序。
 */
@Slf4j
@Service
public class RrfFusionService {

    /** 平滑常数，排名靠后的结果不会被完全忽略 */
    private static final int DEFAULT_K = 60;

    /**
     * RRF 融合两路检索结果
     *
     * @param vectorResults  向量检索结果（已按相似度降序排列）
     * @param keywordResults 关键词检索结果（已按匹配度降序排列）
     * @param k              平滑常数
     * @return 融合后按 RRF 分数降序排列的结果
     */
    public List<ScoredChunk> fuse(List<DocumentChunk> vectorResults,
                                   List<DocumentChunk> keywordResults,
                                   int k) {
        Map<String, Double> rrfScores = new LinkedHashMap<>();
        Map<String, DocumentChunk> chunkMap = new LinkedHashMap<>();

        // 向量路：rank 从 1 开始
        for (int i = 0; i < vectorResults.size(); i++) {
            DocumentChunk chunk = vectorResults.get(i);
            String id = chunk.getId();
            rrfScores.merge(id, 1.0 / (k + i + 1), Double::sum);
            chunkMap.putIfAbsent(id, chunk);
        }

        // 关键词路：rank 从 1 开始
        for (int i = 0; i < keywordResults.size(); i++) {
            DocumentChunk chunk = keywordResults.get(i);
            String id = chunk.getId();
            rrfScores.merge(id, 1.0 / (k + i + 1), Double::sum);
            chunkMap.putIfAbsent(id, chunk);
        }

        List<ScoredChunk> result = rrfScores.entrySet().stream()
                .sorted(Map.Entry.<String, Double>comparingByValue().reversed())
                .map(e -> new ScoredChunk(chunkMap.get(e.getKey()), e.getValue()))
                .collect(Collectors.toList());

        log.debug("RRF fusion: vector={}, keyword={}, fused={}",
                vectorResults.size(), keywordResults.size(), result.size());
        return result;
    }

    /**
     * RRF 融合（使用默认 k=60）
     */
    public List<ScoredChunk> fuse(List<DocumentChunk> vectorResults,
                                   List<DocumentChunk> keywordResults) {
        return fuse(vectorResults, keywordResults, DEFAULT_K);
    }

    /**
     * 带 RRF 分数的 chunk
     */
    public record ScoredChunk(DocumentChunk chunk, double score) {}
}
