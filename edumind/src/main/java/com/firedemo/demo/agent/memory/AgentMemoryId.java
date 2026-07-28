package com.firedemo.demo.agent.memory;

import com.firedemo.demo.agent.context.AgentExecutionContext;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/** A trusted, user-scoped identity for one conversational memory. */
public record AgentMemoryId(Long userId, String sessionId) {

    public AgentMemoryId {
        if (sessionId == null || sessionId.isBlank()) {
            throw new IllegalArgumentException("sessionId is required for agent memory");
        }
        sessionId = sessionId.trim();
    }

    public static AgentMemoryId from(AgentExecutionContext context) {
        if (context == null) {
            throw new IllegalArgumentException("execution context is required for agent memory");
        }
        return new AgentMemoryId(context.userId(), context.sessionId());
    }

    /** Stable database key without exposing user or session identifiers in logs. */
    public String storageKey() {
        String scope = (userId == null ? "anonymous" : userId.toString()) + "\n" + sessionId;
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(scope.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is unavailable", e);
        }
    }

    @Override
    public String toString() {
        return "AgentMemoryId[" + storageKey().substring(0, 12) + "]";
    }
}
