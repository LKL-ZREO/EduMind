package com.firedemo.demo.infrastructure.ai;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class StructuredOutputInvokerTest {

    private SimpleMeterRegistry registry;
    private StructuredOutputInvoker invoker;

    @BeforeEach
    void setUp() {
        registry = new SimpleMeterRegistry();
        Validator validator = Validation.buildDefaultValidatorFactory().getValidator();
        invoker = new StructuredOutputInvoker(
                new ObjectMapper(), validator, new LlmJsonRepairer(),
                new StructuredOutputMetrics(registry), 2, 10_000);
    }

    @Test
    void repairsMalformedJsonBeforeTypedValidation() {
        Score result = invoker.invoke(
                ignored -> "```json\n{'score': 88, 'comment': 'good',}\n```",
                "grade", Score.class, "grading");

        assertThat(result.score).isEqualTo(88);
        assertThat(result.comment).isEqualTo("good");
        assertThat(counter("grading", "repaired_success")).isEqualTo(1.0);
        assertThat(registry.find(StructuredOutputMetrics.REPAIR_DURATION).timer().count()).isEqualTo(1);
    }

    @Test
    void retriesWithSemanticErrorAndPreviousOutput() {
        AtomicInteger calls = new AtomicInteger();
        List<String> prompts = new ArrayList<>();

        Score result = invoker.invoke(prompt -> {
            prompts.add(prompt);
            return calls.getAndIncrement() == 0
                    ? "{\"score\":101,\"comment\":\"too high\"}"
                    : "{\"score\":95,\"comment\":\"corrected\"}";
        }, "grade", Score.class, "grading submissionId=1");

        assertThat(result.score).isEqualTo(95);
        assertThat(prompts).hasSize(2);
        assertThat(prompts.get(1))
                .contains("SEMANTIC_VALIDATION_FAILED")
                .contains("Previous output")
                .contains("101");
        assertThat(counter("grading", "semantic_invalid")).isEqualTo(1.0);
        assertThat(counter("grading", "retry_success")).isEqualTo(1.0);
    }

    @Test
    void exhaustsRetriesWhenRequiredFieldsStayMissing() {
        assertThatThrownBy(() -> invoker.invoke(
                ignored -> "{\"score\":50}", "grade", Score.class, "grading"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("after 2 attempts")
                .hasMessageContaining("comment");

        assertThat(counter("grading", "semantic_invalid")).isEqualTo(2.0);
        assertThat(counter("grading", "retry_exhausted")).isEqualTo(1.0);
    }

    @Test
    void rejectsOversizedResponsesBeforeRepair() {
        StructuredOutputInvoker smallLimit = new StructuredOutputInvoker(
                new ObjectMapper(), Validation.buildDefaultValidatorFactory().getValidator(),
                new LlmJsonRepairer(), new StructuredOutputMetrics(registry), 1, 1_000);

        assertThatThrownBy(() -> smallLimit.invoke(
                ignored -> "x".repeat(1_001), "grade", Score.class, "grading"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("RESPONSE_TOO_LARGE");
    }

    @Test
    void rejectsUnknownPropertiesInsteadOfSilentlyDroppingThem() {
        assertThatThrownBy(() -> invoker.invoke(
                ignored -> "{\"score\":80,\"comment\":\"ok\",\"invented\":true}",
                "grade", Score.class, "grading"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("invented");

        assertThat(counter("grading", "syntax_invalid")).isEqualTo(2.0);
    }

    private double counter(String useCase, String outcome) {
        return registry.find(StructuredOutputMetrics.REQUESTS)
                .tags("use_case", useCase, "outcome", outcome)
                .counter().count();
    }

    static class Score {
        @NotNull @Max(100)
        public Integer score;
        @NotBlank
        public String comment;
    }
}
