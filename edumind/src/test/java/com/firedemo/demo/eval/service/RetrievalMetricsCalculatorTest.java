package com.firedemo.demo.eval.service;

import com.firedemo.demo.Entity.DocumentChunk;
import com.firedemo.demo.eval.model.EvalCase;
import com.firedemo.demo.rag.RrfFusionService;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class RetrievalMetricsCalculatorTest {

    private final RetrievalMetricsCalculator calculator = new RetrievalMetricsCalculator();

    @Test
    void calculatesCoverageRankingAndNdcgFromExpectedEvidence() {
        EvalCase evalCase = EvalCase.builder()
                .expectedKeywords(List.of("指针", "地址"))
                .expectedContent(List.of("指针保存变量地址", "使用星号解引用"))
                .build();

        RetrievalMetricsCalculator.Metrics metrics = calculator.calculate(evalCase, List.of(
                scored("1", "doc-a", "无关的数组内容"),
                scored("2", "doc-a", "指针保存变量地址，使用星号解引用")));

        assertThat(metrics.keywordRecall()).isEqualTo(1.0);
        assertThat(metrics.contentCoverage()).isEqualTo(1.0);
        assertThat(metrics.reciprocalRank()).isEqualTo(0.5);
        assertThat(metrics.ndcg()).isBetween(0.0, 1.0).isLessThan(1.0);
        assertThat(metrics.hit()).isTrue();
    }

    @Test
    void fallsBackToExpectedSourceDocumentForGeneratedDatasets() {
        EvalCase evalCase = EvalCase.builder().sourceDocId("expected-doc").build();

        RetrievalMetricsCalculator.Metrics metrics = calculator.calculate(evalCase, List.of(
                scored("1", "other-doc", "first"),
                scored("2", "expected-doc", "second")));

        assertThat(metrics.keywordRecall()).isEqualTo(-1);
        assertThat(metrics.contentCoverage()).isEqualTo(-1);
        assertThat(metrics.reciprocalRank()).isEqualTo(0.5);
        assertThat(metrics.hit()).isTrue();
    }

    private RrfFusionService.ScoredChunk scored(String id, String docId, String content) {
        DocumentChunk chunk = new DocumentChunk();
        chunk.setId(id);
        chunk.setDocumentId(docId);
        chunk.setContent(content);
        return new RrfFusionService.ScoredChunk(chunk, 1.0);
    }
}
