package com.firedemo.demo.agent.context;

import java.util.Map;

/** A sanitized, user-visible event emitted while an agent run is executing. */
public record AgentUiEvent(String type, Map<String, Object> payload) {

    public AgentUiEvent {
        if (type == null || type.isBlank()) throw new IllegalArgumentException("event type is required");
        payload = payload == null ? Map.of() : Map.copyOf(payload);
    }
}
