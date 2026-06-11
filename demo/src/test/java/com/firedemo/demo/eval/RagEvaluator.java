package com.firedemo.demo.eval;

import com.firedemo.demo.Entity.DocumentChunk;
import com.firedemo.demo.Service.OpenClawService;
import com.firedemo.demo.common.prompt.PromptLoader;
import com.firedemo.demo.rag.EmbeddingService;
import com.firedemo.demo.rag.RerankerService;
import com.firedemo.demo.rag.RrfFusionService;
import com.firedemo.demo.rag.RrfFusionService.ScoredChunk;
import com.firedemo.demo.rag.VectorStoreService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.util.List;
import java.util.stream.Collectors;

/**
 * RAG 评估运行器
 * <p>
 * 第一轮：检索质量评估（不调 LLM，秒出）— Hit@3 / Hit@5 / MRR / Recall
 * 第二轮：生成质量评估（LLM-as-Judge，仅前 5 条）— 忠实度 / 相关性
 */
@Slf4j
@RequiredArgsConstructor
public class RagEvaluator {

    private final EvalDataset dataset;
    private final EmbeddingService embeddingService;
    private final VectorStoreService vectorStoreService;
    private final RrfFusionService rrfFusionService;
    private final RerankerService rerankerService;
    private final OpenClawService openClawService;
    private final PromptLoader promptLoader;

    private static final int TOP_K = 30;
    /** Reranker 最多处理候选数（控制精排耗时） */
    private static final int RERANK_CANDIDATES = 15;

    /**
     * 执行完整评估（两轮）
     */
    public EvalReport evaluate() {
        if (dataset.getAll().isEmpty()) {
            log.info("评估数据集为空，跳过 RAG 评估");
            return new EvalReport();
        }
        log.info("========== RAG Evaluation Started ({} cases) ==========", dataset.size());

        // ==================== 第一轮：检索质量 ====================
        EvalReport report = new EvalReport();
        report.setTotalCases(dataset.size());

        long vectorTotalNs = 0, keywordTotalNs = 0, rrfTotalNs = 0, rerankerTotalNs = 0;

        for (TestCase tc : dataset.getAll()) {
            // 向量检索
            long t0 = System.nanoTime();
            float[] queryEmbedding = embeddingService.embed(tc.getQuery());
            List<DocumentChunk> vectorResults = vectorStoreService.similaritySearch(queryEmbedding, TOP_K);
            long t1 = System.nanoTime();
            vectorTotalNs += (t1 - t0);

            // 关键词检索
            long t2 = System.nanoTime();
            List<VectorStoreService.ScoredChunk> kwResults = vectorStoreService.keywordSearch(tc.getQuery(), TOP_K);
            List<DocumentChunk> keywordResults = kwResults.stream()
                    .map(VectorStoreService.ScoredChunk::chunk)
                    .collect(Collectors.toList());
            long t3 = System.nanoTime();
            keywordTotalNs += (t3 - t2);

            // RRF 融合
            long t4 = System.nanoTime();
            List<ScoredChunk> fused = rrfFusionService.fuse(vectorResults, keywordResults, 60);
            long t5 = System.nanoTime();
            rrfTotalNs += (t5 - t4);

            // DEBUG: 打印 Top-10，确认 C 语言 chunk 是否在索引中
            if (tc.getId() == 1) {
                log.info("  [DEBUG] 向量检索={}条, 关键词检索={}条",
                        vectorResults.size(), kwResults.size());
                for (int i = 0; i < Math.min(10, fused.size()); i++) {
                    ScoredChunk sc = fused.get(i);
                    String content = sc.chunk().getContent();
                    String docName = sc.chunk().getDocumentName();
                    log.info("  [DEBUG] fused[{}] score={} doc={} content={}", i,
                            String.format("%.3f", sc.score()),
                            docName != null ? docName : "NULL",
                            content != null ? content.substring(0, Math.min(60, content.length())).replace("\n", " ") : "NULL");
                }
            }

            // Reranker 精排（只处理 Top-N 候选控制耗时）
            long t6 = System.nanoTime();
            List<ScoredChunk> finalResults;
            if (rerankerService.isModelReady()) {
                List<ScoredChunk> toRerank = fused.size() > RERANK_CANDIDATES
                        ? fused.subList(0, RERANK_CANDIDATES) : fused;
                finalResults = rerankerService.rerank(tc.getQuery(), toRerank, 5, null);
            } else {
                finalResults = fused;
            }
            long t7 = System.nanoTime();
            rerankerTotalNs += (t7 - t6);

            // 计算指标
            EvalReport.CaseResult cr = new EvalReport.CaseResult();
            cr.setId(tc.getId());
            cr.setQuery(tc.getQuery());
            cr.setHit3(MetricCalculator.hitAtK(finalResults, tc.getExpectedContent(), 3));
            cr.setHit5(MetricCalculator.hitAtK(finalResults, tc.getExpectedContent(), 5));
            cr.setReciprocalRank(MetricCalculator.reciprocalRank(finalResults, tc.getExpectedContent()));
            cr.setRecall(MetricCalculator.recallAtK(finalResults, tc.getExpectedContent(), 5,
                    tc.getMinChunksToCover()));

            if (cr.isHit3()) report.setHitAt3(report.getHitAt3() + 1);
            if (cr.isHit5()) report.setHitAt5(report.getHitAt5() + 1);
            report.setMrr(report.getMrr() + cr.getReciprocalRank());
            report.setAvgRecall(report.getAvgRecall() + cr.getRecall());
            report.getDetails().add(cr);
        }

        totalizeRetrievalMetrics(report, vectorTotalNs, keywordTotalNs, rrfTotalNs, rerankerTotalNs);
        log.info(report.format());

        // ==================== 第二轮：生成质量 ====================
        evaluateGenerationQuality(report);

        return report;
    }

    private void totalizeRetrievalMetrics(EvalReport report,
                                           long vectorNs, long keywordNs, long rrfNs, long rerankerNs) {
        int n = report.getTotalCases();
        report.setHitAt3Rate((double) report.getHitAt3() / n);
        report.setHitAt5Rate((double) report.getHitAt5() / n);
        report.setMrr(report.getMrr() / n);
        report.setAvgRecall(report.getAvgRecall() / n);
        report.setAvgVectorSearchMs(vectorNs / n / 1_000_000.0);
        report.setAvgKeywordSearchMs(keywordNs / n / 1_000_000.0);
        report.setAvgRrfFusionMs(rrfNs / n / 1_000_000.0);
        report.setAvgRerankerMs(rerankerNs / n / 1_000_000.0);
    }

    private void evaluateGenerationQuality(EvalReport report) {
        List<TestCase> genCases = dataset.sampleForGenRound(5);
        if (genCases.isEmpty()) return;

        log.info("第二轮：生成质量评估（LLM-as-Judge），{} 条用例", genCases.size());
        double faithSum = 0, relevSum = 0;

        for (TestCase tc : genCases) {
            try {
                // RAG 生成回答
                float[] queryEmbedding = embeddingService.embed(tc.getQuery());
                List<DocumentChunk> vectorResults = vectorStoreService.similaritySearch(queryEmbedding, TOP_K);
                List<DocumentChunk> keywordResults = vectorStoreService.keywordSearch(tc.getQuery(), TOP_K)
                        .stream().map(VectorStoreService.ScoredChunk::chunk).collect(Collectors.toList());
                List<ScoredChunk> fused = rrfFusionService.fuse(vectorResults, keywordResults, 60);
                List<ScoredChunk> toRerank = fused.size() > RERANK_CANDIDATES
                        ? fused.subList(0, RERANK_CANDIDATES) : fused;
                List<ScoredChunk> finalResults = rerankerService.isModelReady()
                        ? rerankerService.rerank(tc.getQuery(), toRerank, 5, null) : toRerank;

                String context = finalResults.stream()
                        .map(sc -> sc.chunk().getContent())
                        .collect(Collectors.joining("\n---\n"));
                String answer = openClawService.chat(
                        "根据以下参考资料回答问题。\n\n参考资料：\n" + context
                                + "\n\n问题：" + tc.getQuery(), null);

                // LLM-as-Judge：忠实度
                int faith = judge("judge-faithfulness.txt",
                        "{{answer}}", answer,
                        "{{context}}", context);
                // LLM-as-Judge：相关性
                int relev = judge("judge-relevance.txt",
                        "{{answer}}", answer,
                        "{{query}}", tc.getQuery());

                faithSum += faith;
                relevSum += relev;

                log.info("  case#{}: faithfulness={}, relevance={} | query={}",
                        tc.getId(), faith, relev, tc.getQuery());

            } catch (Exception e) {
                log.warn("生成质量评估失败 case#{}: {}", tc.getId(), e.getMessage());
            }
        }

        report.setGenCases(genCases.size());
        report.setAvgFaithfulness(faithSum / genCases.size());
        report.setAvgRelevance(relevSum / genCases.size());

        log.info("[生成质量] 忠实度={}, 相关性={}",
                String.format("%.1f", report.getAvgFaithfulness()),
                String.format("%.1f", report.getAvgRelevance()));
    }

    private int judge(String promptFile, String... replacements) {
        String template = promptLoader.load(promptFile);
        String prompt = template;
        for (int i = 0; i < replacements.length; i += 2) {
            prompt = prompt.replace(replacements[i], replacements[i + 1]);
        }
        String result = openClawService.chat(prompt, null);
        if (result == null || result.isBlank()) return 3;
        try {
            String trimmed = result.trim();
            int score = Integer.parseInt(trimmed.replaceAll("[^0-9]", "").substring(0, 1));
            return Math.min(5, Math.max(1, score));
        } catch (Exception e) {
            log.debug("Judge 评分解析失败: {}", result);
            return 3;
        }
    }
}
