package com.firedemo.demo.config.observability;

import com.firedemo.demo.agent.observability.AgentToolMetrics;
import com.firedemo.demo.rag.RagMetrics;
import com.firedemo.demo.rag.RagTrace;
import io.micrometer.prometheusmetrics.PrometheusConfig;
import io.micrometer.prometheusmetrics.PrometheusMeterRegistry;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class LocalAiMetricsPrometheusContractTest {

    @Test
    void exposesMetricNamesUsedByTheGrafanaDashboard() {
        PrometheusMeterRegistry registry = new PrometheusMeterRegistry(PrometheusConfig.DEFAULT);
        RagMetrics ragMetrics = new RagMetrics(registry);
        AgentToolMetrics toolMetrics = new AgentToolMetrics(registry);
        RagTrace trace = new RagTrace("test query");

        ragMetrics.recordStage(trace, RagMetrics.Stage.EMBEDDING, () -> new float[0]);
        ragMetrics.recordCandidates(RagMetrics.CandidateSource.VECTOR, 3);
        ragMetrics.recordSearchOutcome(RagMetrics.SearchOutcome.SUCCESS);
        ragMetrics.recordRewriteOutcome(
                RagMetrics.RewriteReason.INITIAL, RagMetrics.RewriteOutcome.CHANGED);
        toolMetrics.record("searchKnowledge", () -> "ok");

        String scrape = registry.scrape();

        assertThat(scrape)
                .contains("edumind_rag_stage_duration_seconds_bucket")
                .contains("edumind_rag_stage_duration_seconds_count")
                .contains("edumind_rag_candidates_documents_count")
                .contains("edumind_rag_candidates_documents_sum")
                .contains("edumind_rag_searches_total")
                .contains("edumind_rag_rewrites_total")
                .contains("edumind_agent_tool_duration_seconds_bucket")
                .contains("edumind_agent_tool_duration_seconds_count");
    }
}
