package com.firedemo.edumind.assistant.evaluation.model;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class EvalResponseTest {

    @Test
    void readsPercentageKeysProducedByEvalService() {
        EvalResponse response = EvalResponse.builder()
                .status("ok")
                .summary(Map.of(
                        "faithfulness_pct", 92.5,
                        "answer_relevancy_pct", 88.0))
                .build();

        assertThat(response.faithfulness()).isEqualTo(92.5);
        assertThat(response.answerRelevancy()).isEqualTo(88.0);
    }
}
