package com.firedemo.demo.eval.service;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.MultiGauge;
import io.micrometer.core.instrument.Tags;
import org.springframework.stereotype.Component;

import java.util.Map;

/** Publishes the latest normalized quality scores for Grafana and alerts. */
@Component
public class EvalMetricsPublisher {

    public static final String SCORE = "edumind.rag.eval.score";
    public static final String RUNS = "edumind.rag.eval.runs";

    private final MeterRegistry registry;
    private final MultiGauge scores;

    public EvalMetricsPublisher(MeterRegistry registry) {
        this.registry = registry;
        this.scores = MultiGauge.builder(SCORE)
                .description("Latest normalized RAG evaluation score")
                .register(registry);
    }

    public void publish(Map<String, Double> summary, boolean qualityGatePassed) {
        scores.register(summary.entrySet().stream()
                .filter(entry -> isScore(entry.getKey()))
                .map(entry -> MultiGauge.Row.of(Tags.of("metric", entry.getKey()), entry.getValue()))
                .toList(), true);

        Counter.builder(RUNS)
                .description("Completed RAG evaluation runs")
                .tag("outcome", qualityGatePassed ? "passed" : "failed_gate")
                .register(registry)
                .increment();
    }

    public void publishFailure() {
        Counter.builder(RUNS)
                .description("Completed RAG evaluation runs")
                .tag("outcome", "error")
                .register(registry)
                .increment();
    }

    private boolean isScore(String key) {
        return switch (key) {
            case "keyword_recall", "content_coverage", "hit_rate", "mrr", "ndcg",
                    "faithfulness", "answer_relevancy" -> true;
            default -> false;
        };
    }
}
