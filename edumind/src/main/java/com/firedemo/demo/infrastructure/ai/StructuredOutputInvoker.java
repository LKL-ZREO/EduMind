package com.firedemo.demo.infrastructure.ai;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.firedemo.demo.common.util.JsonUtil;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * Reliable LLM structured-output boundary: strict parse, repair fallback,
 * semantic validation, and bounded retry feedback.
 */
@Slf4j
@Component
public class StructuredOutputInvoker {

    private static final int LOG_TRUNCATE_CHARS = 200;
    private static final int ERROR_MSG_TRUNCATE_CHARS = 500;
    private static final int PREVIOUS_OUTPUT_TRUNCATE_CHARS = 1_500;

    private final ObjectMapper objectMapper;
    private final Validator validator;
    private final LlmJsonRepairer jsonRepairer;
    private final StructuredOutputMetrics metrics;
    private final int maxAttempts;
    private final int maxResponseChars;

    public StructuredOutputInvoker(
            ObjectMapper objectMapper,
            Validator validator,
            LlmJsonRepairer jsonRepairer,
            StructuredOutputMetrics metrics,
            @Value("${app.ai.structured-max-attempts:2}") int maxAttempts,
            @Value("${app.ai.structured-max-response-chars:100000}") int maxResponseChars) {
        this.objectMapper = objectMapper;
        this.validator = validator;
        this.jsonRepairer = jsonRepairer;
        this.metrics = metrics;
        this.maxAttempts = Math.max(1, maxAttempts);
        this.maxResponseChars = Math.max(1_000, maxResponseChars);
    }

    public <T> T invoke(Function<String, String> llmCall, String prompt,
                        Class<T> clazz, String logCtx) {
        return invoke(llmCall, prompt, clazz, logCtx, ignored -> { });
    }

    /**
     * Invoke and validate structured output. The additional validator should
     * throw {@link StructuredOutputValidationException} for cross-field rules.
     */
    public <T> T invoke(Function<String, String> llmCall, String prompt,
                        Class<T> clazz, String logCtx, Consumer<T> additionalValidator) {
        RuntimeException lastError = null;
        String lastResponse = null;
        String useCase = metricUseCase(logCtx);

        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            String finalPrompt = attempt == 1
                    ? prompt
                    : buildRetryPrompt(prompt, lastError, lastResponse);

            try {
                lastResponse = requireBoundedResponse(llmCall.apply(finalPrompt));
                String rawResponse = lastResponse;
                log.debug("{} LLM response (attempt={}): {}", logCtx, attempt,
                        truncate(lastResponse, LOG_TRUNCATE_CHARS));

                T result;
                try {
                    result = objectMapper.readValue(JsonUtil.extractJson(rawResponse), clazz);
                } catch (JsonProcessingException | IllegalArgumentException strictError) {
                    String repaired = metrics.recordRepair(() -> jsonRepairer.repair(rawResponse));
                    result = objectMapper.readValue(requireBoundedResponse(repaired), clazz);
                    validate(result, additionalValidator);
                    metrics.record(useCase, "repaired_success");
                    log.info("{} structured output repaired (attempt={})", logCtx, attempt);
                    return result;
                }

                validate(result, additionalValidator);
                metrics.record(useCase, attempt == 1 ? "strict_success" : "retry_success");
                log.info("{} structured output succeeded (attempt={})", logCtx, attempt);
                return result;
            } catch (StructuredOutputValidationException e) {
                lastError = e;
                metrics.record(useCase, "semantic_invalid");
                log.warn("{} semantic validation failed (attempt={}/{}): {}",
                        logCtx, attempt, maxAttempts, e.getMessage());
            } catch (JsonProcessingException | RuntimeException e) {
                lastError = e instanceof RuntimeException runtime
                        ? runtime : new IllegalArgumentException("SYNTAX_INVALID: " + e.getMessage(), e);
                metrics.record(useCase, "syntax_invalid");
                log.warn("{} structured parse failed (attempt={}/{}): {}",
                        logCtx, attempt, maxAttempts, e.getMessage());
            }
        }

        metrics.record(useCase, "retry_exhausted");
        throw new IllegalStateException(
                logCtx + " structured output failed after " + maxAttempts + " attempts: "
                        + (lastError != null ? truncate(lastError.getMessage(), ERROR_MSG_TRUNCATE_CHARS) : "unknown"),
                lastError);
    }

    private <T> void validate(T result, Consumer<T> additionalValidator) {
        if (result == null) {
            throw new StructuredOutputValidationException(List.of("root: must not be null"));
        }
        Set<ConstraintViolation<T>> violations = validator.validate(result);
        if (!violations.isEmpty()) {
            List<String> messages = violations.stream()
                    .sorted(Comparator.comparing(v -> v.getPropertyPath().toString()))
                    .map(v -> v.getPropertyPath() + ": " + v.getMessage())
                    .toList();
            throw new StructuredOutputValidationException(messages);
        }
        additionalValidator.accept(result);
    }

    private String requireBoundedResponse(String response) {
        if (response == null || response.isBlank()) {
            throw new IllegalArgumentException("EMPTY_RESPONSE: LLM returned no content");
        }
        if (response.length() > maxResponseChars) {
            throw new IllegalArgumentException(
                    "RESPONSE_TOO_LARGE: " + response.length() + " > " + maxResponseChars);
        }
        return response.trim();
    }

    private String buildRetryPrompt(String originalPrompt, RuntimeException lastError,
                                    String previousOutput) {
        StringBuilder prompt = new StringBuilder(originalPrompt);
        prompt.append("\n\nYour previous structured output was rejected. Correct it and try again.\n")
                .append("Return exactly one JSON object without markdown fences or explanatory text.\n")
                .append("Validation error: ")
                .append(truncate(lastError != null ? lastError.getMessage() : "unknown",
                        ERROR_MSG_TRUNCATE_CHARS));
        if (previousOutput != null) {
            prompt.append("\nPrevious output (possibly truncated):\n")
                    .append(truncate(previousOutput, PREVIOUS_OUTPUT_TRUNCATE_CHARS));
        }
        return prompt.toString();
    }

    private String metricUseCase(String logCtx) {
        if (logCtx == null || logCtx.isBlank()) return "unknown";
        String firstToken = logCtx.trim().split("\\s+", 2)[0];
        return firstToken.replaceAll("[^a-zA-Z0-9_-]", "_").toLowerCase();
    }

    private String truncate(String value, int maxChars) {
        if (value == null) return "null";
        return value.length() <= maxChars ? value : value.substring(0, maxChars) + "...";
    }
}
