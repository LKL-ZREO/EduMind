package com.firedemo.demo.eval;

import lombok.Data;
import java.util.ArrayList;
import java.util.List;

/**
 * RAG 评估报告 DTO
 */
@Data
public class EvalReport {

    /** 总用例数 */
    private int totalCases;

    // ===== 第一轮：检索质量（不调 LLM） =====
    private int hitAt3;
    private int hitAt5;
    private double hitAt3Rate;
    private double hitAt5Rate;
    private double mrr;
    private double avgRecall;

    // ===== 第二轮：生成质量（LLM-as-Judge） =====
    private int genCases;
    private double avgFaithfulness;  // 忠实度 1-5
    private double avgRelevance;     // 相关性 1-5

    // ===== 耗时统计（单条平均 ms） =====
    private double avgVectorSearchMs;
    private double avgKeywordSearchMs;
    private double avgRrfFusionMs;
    private double avgRerankerMs;

    // ===== 明细 =====
    private List<CaseResult> details = new ArrayList<>();

    @Data
    public static class CaseResult {
        private int id;
        private String query;
        private boolean hit3;
        private boolean hit5;
        private double reciprocalRank;
        private double recall;
        private Integer faithfulness;
        private Integer relevance;
    }

    public String format() {
        StringBuilder sb = new StringBuilder();
        sb.append("\n========== RAG Evaluation Report ==========\n");
        sb.append(String.format("测试用例数: %d%n%n", totalCases));

        sb.append("[检索质量]\n");
        sb.append(String.format("  Hit@3:     %.1f%%  (%d/%d)%n", hitAt3Rate * 100, hitAt3, totalCases));
        sb.append(String.format("  Hit@5:     %.1f%%  (%d/%d)%n", hitAt5Rate * 100, hitAt5, totalCases));
        sb.append(String.format("  MRR:       %.3f%n", mrr));
        sb.append(String.format("  Recall@5:  %.1f%%%n%n", avgRecall * 100));

        if (genCases > 0) {
            sb.append("[生成质量] (LLM-as-Judge, 1-5分)\n");
            sb.append(String.format("  忠实度:    %.1f%n", avgFaithfulness));
            sb.append(String.format("  相关性:    %.1f%n%n", avgRelevance));
        }

        sb.append("[耗时] (单条平均)\n");
        sb.append(String.format("  向量检索:    %.1f ms%n", avgVectorSearchMs));
        sb.append(String.format("  关键词检索:  %.1f ms%n", avgKeywordSearchMs));
        sb.append(String.format("  RRF 融合:    %.1f ms%n", avgRrfFusionMs));
        sb.append(String.format("  Reranker:    %.1f ms%n", avgRerankerMs));
        sb.append("============================================\n");
        return sb.toString();
    }
}
