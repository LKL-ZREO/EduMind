package com.firedemo.demo.eval;

import com.firedemo.demo.rag.RrfFusionService.ScoredChunk;

import java.util.List;

/**
 * RAG 检索质量指标计算器
 */
public class MetricCalculator {

    /**
     * Hit@K: Top-K 结果中是否命中至少一个期望内容
     */
    public static boolean hitAtK(List<ScoredChunk> results, List<String> expectedContents, int k) {
        int bound = Math.min(k, results.size());
        for (int i = 0; i < bound; i++) {
            String content = results.get(i).chunk().getContent();
            if (content != null && containsAny(content, expectedContents)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Reciprocal Rank: 第一条匹配内容的排名倒数
     */
    public static double reciprocalRank(List<ScoredChunk> results, List<String> expectedContents) {
        for (int i = 0; i < results.size(); i++) {
            String content = results.get(i).chunk().getContent();
            if (content != null && containsAny(content, expectedContents)) {
                return 1.0 / (i + 1);
            }
        }
        return 0.0;
    }

    /**
     * Recall@K: Top-K 覆盖了期望内容的比例
     */
    public static double recallAtK(List<ScoredChunk> results, List<String> expectedContents,
                                    int k, int minToCover) {
        int bound = Math.min(k, results.size());
        int covered = 0;
        for (String expected : expectedContents) {
            for (int i = 0; i < bound; i++) {
                String content = results.get(i).chunk().getContent();
                if (content != null && content.contains(expected)) {
                    covered++;
                    break;
                }
            }
        }
        int denominator = Math.max(expectedContents.size(), minToCover);
        return denominator > 0 ? (double) covered / denominator : 0.0;
    }

    private static boolean containsAny(String text, List<String> keywords) {
        for (String kw : keywords) {
            if (text.contains(kw)) return true;
        }
        return false;
    }
}
