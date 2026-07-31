package com.firedemo.edumind.assistant.evaluation.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.firedemo.edumind.knowledge.retrieval.DocumentChunk;
import com.firedemo.edumind.assistant.AgentService;
import com.firedemo.edumind.assistant.config.LlmProperties;
import com.firedemo.edumind.assistant.evaluation.config.EvalProperties;
import com.firedemo.edumind.assistant.evaluation.model.EvalCase;
import com.firedemo.edumind.assistant.evaluation.model.EvalResponse;
import com.firedemo.edumind.assistant.evaluation.model.EvalRunRequest;
import com.firedemo.edumind.assistant.evaluation.persistence.EvalRunStore;
import com.firedemo.edumind.assistant.structured.StructuredOutputInvoker;
import com.firedemo.edumind.knowledge.retrieval.persistence.DocumentChunkMapper;
import com.firedemo.edumind.knowledge.retrieval.RagResult;
import com.firedemo.edumind.knowledge.retrieval.RagService;
import com.firedemo.edumind.knowledge.retrieval.RrfFusionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class EvalServiceTest {

    private RagService ragService;
    private AgentService agentService;
    private EvalRunStore runStore;
    private EvalService service;

    @BeforeEach
    void setUp() {
        ragService = mock(RagService.class);
        agentService = mock(AgentService.class);
        runStore = mock(EvalRunStore.class);
        EvalProperties properties = new EvalProperties();
        LlmProperties llm = new LlmProperties();
        llm.setTextModel("test-model");

        service = spy(new EvalService(
                ragService,
                agentService,
                mock(DocumentChunkMapper.class),
                new ObjectMapper(),
                mock(StructuredOutputInvoker.class),
                new RetrievalMetricsCalculator(),
                runStore,
                mock(EvalMetricsPublisher.class),
                properties,
                llm));
        when(runStore.start(any())).thenReturn(42L);
    }

    @Test
    void generateAnswersEvaluatesTheActualPipelineAnswerInsteadOfReferenceAnswer() {
        EvalCase evalCase = EvalCase.builder()
                .id(7)
                .query("什么是指针？")
                .answer("legacy reference")
                .groundTruth("ground truth reference")
                .sourceDocId("doc-1")
                .build();
        doReturn(List.of(evalCase)).when(service).loadDataset();
        when(ragService.search(any())).thenReturn(ragResult());
        when(agentService.chat(contains("什么是指针"), eq("eval-gen")))
                .thenReturn("actual generated answer");

        EvalRunRequest request = new EvalRunRequest();
        request.setMetrics(List.of("mrr"));
        request.setGenerateAnswers(true);
        request.setThresholds(Map.of("mrr", 0.5));

        EvalResponse response = service.runEvaluation(request);

        assertThat(response.isOk()).isTrue();
        assertThat(response.getRunId()).isEqualTo(42L);
        assertThat(response.getPerCase()).singleElement().satisfies(result -> {
            assertThat(result.referenceAnswer()).isEqualTo("ground truth reference");
            assertThat(result.generatedAnswer()).isEqualTo("actual generated answer");
        });
        assertThat(response.isQualityGatePassed()).isTrue();
        verify(agentService).chat(contains("什么是指针"), eq("eval-gen"));
        verify(runStore).saveCase(eq(42L), any());
        verify(runStore).complete(eq(42L), any(), eq(1), anyLong(), eq(true));
    }

    @Test
    void reportsQualityGateFailuresWithoutHidingTheScores() {
        EvalCase evalCase = EvalCase.builder().id(1).query("q").sourceDocId("doc-1").build();
        doReturn(List.of(evalCase)).when(service).loadDataset();
        when(ragService.search(any())).thenReturn(ragResult("other-doc"));

        EvalRunRequest request = new EvalRunRequest();
        request.setMetrics(List.of("mrr"));
        request.setGenerateAnswers(false);
        request.setThresholds(Map.of("mrr", 0.5));

        EvalResponse response = service.runEvaluation(request);

        assertThat(response.isOk()).isTrue();
        assertThat(response.isQualityGatePassed()).isFalse();
        assertThat(response.getQualityGateFailures()).anyMatch(failure -> failure.startsWith("mrr"));
        assertThat(response.getPerCase()).singleElement().satisfies(result -> {
            assertThat(result.referenceAnswer()).isEmpty();
            assertThat(result.generatedAnswer()).isEmpty();
        });
        verify(runStore).complete(eq(42L), any(), eq(1), anyLong(), eq(false));
    }

    private RagResult ragResult() {
        return ragResult("doc-1");
    }

    private RagResult ragResult(String documentId) {
        DocumentChunk chunk = new DocumentChunk();
        chunk.setId("chunk-1");
        chunk.setDocumentId(documentId);
        chunk.setContent("指针保存内存地址");
        return RagResult.builder()
                .hasContext(true)
                .results(List.of(new RrfFusionService.ScoredChunk(chunk, 1.0)))
                .build();
    }
}
