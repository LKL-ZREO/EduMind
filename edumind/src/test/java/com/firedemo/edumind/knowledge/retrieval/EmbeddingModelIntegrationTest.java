package com.firedemo.edumind.knowledge.retrieval;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

@EnabledIfEnvironmentVariable(named = "RUN_EMBEDDING_MODEL_IT", matches = "true")
class EmbeddingModelIntegrationTest {

    @Test
    void downloadsModelAndProducesNormalized512DimensionVectors() {
        EmbeddingService service = new EmbeddingService();
        try {
            service.init();

            float[] document = service.embedDocument("人工智能辅助课堂教学");
            float[] query = service.embedQuery("如何使用人工智能辅助教学？");

            assertThat(document).hasSize(EmbeddingService.EMBEDDING_DIMENSION);
            assertThat(query).hasSize(EmbeddingService.EMBEDDING_DIMENSION);
            assertThat(l2Norm(document)).isCloseTo(1.0, within(0.0001));
            assertThat(l2Norm(query)).isCloseTo(1.0, within(0.0001));
        } finally {
            service.closeModel();
        }
    }

    private static double l2Norm(float[] vector) {
        double squaredSum = 0;
        for (float value : vector) {
            squaredSum += value * value;
        }
        return Math.sqrt(squaredSum);
    }
}
