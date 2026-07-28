package com.firedemo.demo.infrastructure.ai;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.stereotype.Component;

import java.util.function.Supplier;

/** Low-cardinality reliability metrics for LLM structured outputs. */
@Component
public class StructuredOutputMetrics {

    public static final String REQUESTS = "edumind.llm.structured.requests";
    public static final String REPAIR_DURATION = "edumind.llm.structured.repair.duration";

    private final MeterRegistry registry;

    public StructuredOutputMetrics(MeterRegistry registry) {
        this.registry = registry;
    }

    public void record(String useCase, String outcome) {
        Counter.builder(REQUESTS)
                .description("LLM structured output attempts by outcome")
                .tag("use_case", useCase)
                .tag("outcome", outcome)
                .register(registry)
                .increment();
    }

    public <T> T recordRepair(Supplier<T> repair) {
        return Timer.builder(REPAIR_DURATION)
                .description("Malformed LLM JSON repair duration")
                .publishPercentileHistogram()
                .register(registry)
                .record(repair);
    }
}
