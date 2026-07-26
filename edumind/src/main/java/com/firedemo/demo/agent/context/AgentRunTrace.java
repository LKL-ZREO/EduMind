package com.firedemo.demo.agent.context;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.function.Supplier;

/** Thread-safe, request-scoped trace summary for an agent run. */
public final class AgentRunTrace {

    private final String traceId;
    private final Instant startedAt = Instant.now();
    private final long startedNanos = System.nanoTime();
    private final ConcurrentLinkedQueue<ToolCallTrace> toolCalls = new ConcurrentLinkedQueue<>();

    public AgentRunTrace(AgentExecutionContext context) {
        this.traceId = Objects.requireNonNull(context, "context is required").traceId();
    }

    public <T> T traceToolCall(String toolName, Supplier<T> invocation) {
        String safeName = requireToolName(toolName);
        Objects.requireNonNull(invocation, "invocation is required");
        Instant callStartedAt = Instant.now();
        long callStartedNanos = System.nanoTime();
        try {
            T result = invocation.get();
            toolCalls.add(new ToolCallTrace(
                    safeName, callStartedAt, elapsedMillis(callStartedNanos), true, null));
            return result;
        } catch (RuntimeException | Error error) {
            toolCalls.add(new ToolCallTrace(
                    safeName,
                    callStartedAt,
                    elapsedMillis(callStartedNanos),
                    false,
                    error.getClass().getSimpleName()));
            throw error;
        }
    }

    public String traceId() {
        return traceId;
    }

    public Instant startedAt() {
        return startedAt;
    }

    public long elapsedMillis() {
        return elapsedMillis(startedNanos);
    }

    public boolean hasToolCalls() {
        return !toolCalls.isEmpty();
    }

    public int toolCallCount() {
        return toolCalls.size();
    }

    public List<ToolCallTrace> toolCalls() {
        return List.copyOf(toolCalls);
    }

    private long elapsedMillis(long startNanos) {
        return Math.max(0L, (System.nanoTime() - startNanos) / 1_000_000L);
    }

    private String requireToolName(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("toolName is required");
        }
        return value.trim();
    }

    public record ToolCallTrace(
            String toolName,
            Instant startedAt,
            long elapsedMillis,
            boolean success,
            String failureType
    ) {
    }
}
