package com.firedemo.edumind.knowledge.retrieval;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.DistributionSummary;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.stereotype.Component;

import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Supplier;

/** Aggregate, low-cardinality metrics for the RAG pipeline. */
@Component
public class RagMetrics {

    static final String STAGE_DURATION = "edumind.rag.stage.duration";
    static final String CANDIDATES = "edumind.rag.candidates";
    static final String SEARCHES = "edumind.rag.searches";
    static final String REWRITES = "edumind.rag.rewrites";

    private final MeterRegistry registry;

    public RagMetrics(MeterRegistry registry) {
        this.registry = registry;
    }

    public <T> T recordStage(RagTrace trace, Stage stage, Supplier<T> action) {
        if (stage == Stage.REWRITE) {
            throw new IllegalArgumentException("Use recordRewrite() for the rewrite stage");
        }
        return record(trace, stage, action, ignored -> trace.endStep());
    }

    public String recordRewrite(RagTrace trace, Supplier<String> action) {
        return record(trace, Stage.REWRITE, action, trace::endStep);
    }

    public void recordCandidates(CandidateSource source, int count) {
        DistributionSummary.builder(CANDIDATES)
                .description("Number of candidates produced by a RAG stage")
                .baseUnit("documents")
                .tag("source", source.tagValue)
                .register(registry)
                .record(Math.max(0, count));
    }

    public void recordSearchOutcome(SearchOutcome outcome) {
        Counter.builder(SEARCHES)
                .description("Completed RAG searches by outcome")
                .tag("outcome", outcome.tagValue)
                .register(registry)
                .increment();
    }

    public void recordRewriteOutcome(RewriteReason reason, RewriteOutcome outcome) {
        Counter.builder(REWRITES)
                .description("RAG query rewrite attempts by reason and outcome")
                .tag("reason", reason.tagValue)
                .tag("outcome", outcome.tagValue)
                .register(registry)
                .increment();
    }

    private <T> T record(RagTrace trace,
                         Stage stage,
                         Supplier<T> action,
                         Consumer<T> traceCompletion) {
        Objects.requireNonNull(trace, "trace is required");
        Objects.requireNonNull(stage, "stage is required");
        Objects.requireNonNull(action, "action is required");

        trace.step(stage.traceName);
        Timer.Sample sample = Timer.start(registry);
        String outcome = "success";
        boolean traceCompleted = false;

        try {
            T result = action.get();
            traceCompletion.accept(result);
            traceCompleted = true;
            return result;
        } catch (RuntimeException | Error error) {
            outcome = "error";
            throw error;
        } finally {
            if (!traceCompleted) {
                trace.endStep();
            }
            sample.stop(Timer.builder(STAGE_DURATION)
                    .description("RAG pipeline stage duration")
                    .tag("stage", stage.tagValue)
                    .tag("outcome", outcome)
                    .publishPercentileHistogram()
                    .register(registry));
        }
    }

    public enum Stage {
        EMBEDDING("embedding", "embed"),
        VECTOR("vector", "vector"),
        KEYWORD("keyword", "keyword"),
        REWRITE("rewrite", "rewrite"),
        RRF("rrf", "rrf"),
        RERANKER("reranker", "reranker");

        private final String tagValue;
        private final String traceName;

        Stage(String tagValue, String traceName) {
            this.tagValue = tagValue;
            this.traceName = traceName;
        }
    }

    public enum CandidateSource {
        VECTOR("vector"),
        KEYWORD("keyword"),
        EXTRA_KEYWORD("extra_keyword"),
        FUSED("fused"),
        FINAL("final");

        private final String tagValue;

        CandidateSource(String tagValue) {
            this.tagValue = tagValue;
        }
    }

    public enum SearchOutcome {
        SUCCESS("success"),
        EMPTY("empty"),
        ERROR("error");

        private final String tagValue;

        SearchOutcome(String tagValue) {
            this.tagValue = tagValue;
        }
    }

    public enum RewriteReason {
        INITIAL("initial"),
        LOW_CONFIDENCE("low_confidence");

        private final String tagValue;

        RewriteReason(String tagValue) {
            this.tagValue = tagValue;
        }
    }

    public enum RewriteOutcome {
        CHANGED("changed"),
        UNCHANGED("unchanged"),
        ERROR("error");

        private final String tagValue;

        RewriteOutcome(String tagValue) {
            this.tagValue = tagValue;
        }
    }
}
