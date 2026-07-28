package com.firedemo.demo.eval.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

/**
 * RAGAS Python 服务的评估响应。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class EvalResponse {

    private String status;
    private Map<String, Double> summary;
    private List<Map<String, Double>> perCase;
    private int numCases;
    private double evalDurationSeconds;
    private String error;

    public boolean isOk() {
        return "ok".equals(status);
    }

    public Double faithfulness() {
        return summary != null ? summary.getOrDefault("faithfulness", summary.get("faithfulness_pct")) : null;
    }

    public Double answerRelevancy() {
        return summary != null ? summary.getOrDefault("answer_relevancy", summary.get("answer_relevancy_pct")) : null;
    }

    public Double contextPrecision() {
        return summary != null ? summary.get("context_precision") : null;
    }

    public Double contextRecall() {
        return summary != null ? summary.get("context_recall") : null;
    }
}
