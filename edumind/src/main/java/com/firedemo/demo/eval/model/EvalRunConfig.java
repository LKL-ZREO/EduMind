package com.firedemo.demo.eval.model;

import lombok.Builder;

import java.util.List;
import java.util.Map;

/** Immutable configuration snapshot attached to every evaluation run. */
@Builder
public record EvalRunConfig(
        String datasetVersion,
        String datasetHash,
        String gitCommit,
        String llmModel,
        String embeddingModel,
        String rerankerModel,
        int topK,
        boolean rerankerEnabled,
        boolean generateAnswers,
        List<String> metrics,
        Map<String, Double> thresholds
) {
    public EvalRunConfig {
        metrics = metrics == null ? List.of() : List.copyOf(metrics);
        thresholds = thresholds == null ? Map.of() : Map.copyOf(thresholds);
    }
}
