package com.firedemo.demo.agent.observability;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.stereotype.Component;

import java.util.Objects;
import java.util.function.Supplier;

/** Low-cardinality latency and outcome metrics for Agent tool executions. */
@Component
public class AgentToolMetrics {

    public static final String TOOL_DURATION = "edumind.agent.tool.duration";

    private final MeterRegistry registry;

    public AgentToolMetrics(MeterRegistry registry) {
        this.registry = registry;
    }

    public <T> T record(String toolName, Supplier<T> action) {
        String safeToolName = requireToolName(toolName);
        Objects.requireNonNull(action, "action is required");

        Timer.Sample sample = Timer.start(registry);
        String outcome = "success";
        try {
            return action.get();
        } catch (RuntimeException | Error error) {
            outcome = "error";
            throw error;
        } finally {
            sample.stop(Timer.builder(TOOL_DURATION)
                    .description("Agent tool execution duration")
                    .tag("tool", safeToolName)
                    .tag("outcome", outcome)
                    .publishPercentileHistogram()
                    .register(registry));
        }
    }

    private String requireToolName(String toolName) {
        if (toolName == null || toolName.isBlank()) {
            throw new IllegalArgumentException("toolName is required");
        }
        return toolName.trim();
    }
}
