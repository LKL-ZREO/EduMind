package com.firedemo.demo.agent.context;

import java.util.Objects;
import java.util.Set;

/**
 * Immutable, application-supplied context for one logical agent run.
 * This data is trusted and must never be populated from model tool arguments.
 */
public record AgentExecutionContext(
        String sessionId,
        Long userId,
        Long courseId,
        Set<Long> accessibleKbIds,
        AgentChannel channel,
        String traceId
) {

    public AgentExecutionContext {
        sessionId = requireText(sessionId, "sessionId");
        channel = Objects.requireNonNull(channel, "channel is required");
        traceId = requireText(traceId, "traceId");
        accessibleKbIds = accessibleKbIds == null ? Set.of() : Set.copyOf(accessibleKbIds);
    }

    public boolean isAuthenticated() {
        return userId != null;
    }

    public long requireUserId() {
        if (userId == null) {
            throw new IllegalStateException("Authenticated user context is required");
        }
        return userId;
    }

    private static String requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " is required");
        }
        return value.trim();
    }
}
