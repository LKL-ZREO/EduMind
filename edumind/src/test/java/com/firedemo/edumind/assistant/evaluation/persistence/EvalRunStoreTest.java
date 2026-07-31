package com.firedemo.edumind.assistant.evaluation.persistence;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.firedemo.edumind.assistant.evaluation.model.EvalCaseResult;
import com.firedemo.edumind.assistant.evaluation.model.EvalRunConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class EvalRunStoreTest {

    private EvalRunMapper runMapper;
    private EvalCaseResultMapper caseMapper;
    private EvalRunStore store;

    @BeforeEach
    void setUp() {
        runMapper = mock(EvalRunMapper.class);
        caseMapper = mock(EvalCaseResultMapper.class);
        store = new EvalRunStore(runMapper, caseMapper, new ObjectMapper());
    }

    @Test
    void persistsReproducibilityConfigAndPerCaseEvidence() {
        doAnswer(invocation -> {
            EvalRunEntity run = invocation.getArgument(0);
            run.setId(99L);
            return 1;
        }).when(runMapper).insert(any());

        EvalRunConfig config = EvalRunConfig.builder()
                .datasetVersion("rag-v2")
                .datasetHash("abc123")
                .gitCommit("deadbeef")
                .llmModel("qwen-test")
                .embeddingModel("bge-test")
                .rerankerModel("reranker-test")
                .topK(8)
                .rerankerEnabled(true)
                .generateAnswers(true)
                .metrics(List.of("mrr", "faithfulness"))
                .thresholds(Map.of("mrr", 0.4, "faithfulness", 0.7))
                .build();

        Long runId = store.start(config);
        store.saveCase(runId, EvalCaseResult.builder()
                .caseId(3)
                .query("什么是向量检索？")
                .sourceDocId("doc-7")
                .referenceAnswer("reference")
                .generatedAnswer("generated")
                .retrievedChunkIds(List.of("chunk-1", "chunk-2"))
                .keywordRecall(-1)
                .contentCoverage(0.75)
                .reciprocalRank(0.5)
                .ndcg(0.8)
                .retrievalHit(true)
                .faithfulness(true)
                .answerRelevancy(false)
                .retrievalMs(12)
                .generationMs(34)
                .build());
        store.complete(runId, Map.of("mrr", 0.5), 1, 46, true);

        assertThat(runId).isEqualTo(99L);
        ArgumentCaptor<EvalRunEntity> runCaptor = ArgumentCaptor.forClass(EvalRunEntity.class);
        verify(runMapper).insert(runCaptor.capture());
        assertThat(runCaptor.getValue()).satisfies(run -> {
            assertThat(run.getStatus()).isEqualTo("RUNNING");
            assertThat(run.getDatasetHash()).isEqualTo("abc123");
            assertThat(run.getConfigJson()).contains("\"gitCommit\":\"deadbeef\"")
                    .contains("\"topK\":8")
                    .contains("\"thresholds\"")
                    .contains("\"mrr\":0.4");
        });

        ArgumentCaptor<EvalCaseResultEntity> caseCaptor =
                ArgumentCaptor.forClass(EvalCaseResultEntity.class);
        verify(caseMapper).insert(caseCaptor.capture());
        assertThat(caseCaptor.getValue()).satisfies(result -> {
            assertThat(result.getRunId()).isEqualTo(99L);
            assertThat(result.getRetrievedChunkIdsJson()).isEqualTo("[\"chunk-1\",\"chunk-2\"]");
            assertThat(result.getKeywordRecall()).isNull();
            assertThat(result.getContentCoverage()).isEqualTo(0.75);
            assertThat(result.getGeneratedAnswer()).isEqualTo("generated");
        });
        verify(runMapper).complete(99L, "{\"mrr\":0.5}", 1, 46, true);
    }

    @Test
    void capsHistoryLimitAndFailureMessage() {
        store.listRecent(500);
        store.fail(7L, new IllegalStateException("x".repeat(2_500)));

        verify(runMapper).listRecent(100);
        ArgumentCaptor<String> message = ArgumentCaptor.forClass(String.class);
        verify(runMapper).fail(org.mockito.ArgumentMatchers.eq(7L), message.capture());
        assertThat(message.getValue()).hasSize(2_000);
    }
}
