package com.firedemo.edumind.assistant.memory;

import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.ChatMessageDeserializer;
import dev.langchain4j.data.message.ChatMessageSerializer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Objects;

@Slf4j
@Component
public class PostgresAgentChatMemoryStore implements AgentChatMemoryStore {

    private final AgentChatMemoryMapper mapper;

    public PostgresAgentChatMemoryStore(AgentChatMemoryMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public List<ChatMessage> getMessages(Object memoryId) {
        AgentMemoryId id = requireMemoryId(memoryId);
        String json = mapper.selectMessagesJson(id.storageKey());
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            return List.copyOf(ChatMessageDeserializer.messagesFromJson(json));
        } catch (RuntimeException e) {
            throw new IllegalStateException("Stored agent memory cannot be deserialized: " + id, e);
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateMessages(Object memoryId, List<ChatMessage> messages) {
        AgentMemoryId id = requireMemoryId(memoryId);
        Objects.requireNonNull(messages, "messages are required");
        String json = ChatMessageSerializer.messagesToJson(messages);
        mapper.upsert(id.storageKey(), id.userId(), id.sessionId(), json);
        log.debug("Agent memory persisted: memoryId={}, messageCount={}", id, messages.size());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteMessages(Object memoryId) {
        AgentMemoryId id = requireMemoryId(memoryId);
        mapper.deleteByMemoryKey(id.storageKey());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int deleteByUserId(Long userId) {
        Objects.requireNonNull(userId, "userId is required");
        return mapper.deleteByUserId(userId);
    }

    @Override
    public List<AgentMemoryId> findByUserId(Long userId) {
        Objects.requireNonNull(userId, "userId is required");
        List<String> sessionIds = mapper.selectSessionIdsByUserId(userId);
        if (sessionIds == null || sessionIds.isEmpty()) {
            return List.of();
        }
        return sessionIds.stream()
                .map(sessionId -> new AgentMemoryId(userId, sessionId))
                .toList();
    }

    private AgentMemoryId requireMemoryId(Object memoryId) {
        if (!(memoryId instanceof AgentMemoryId id)) {
            throw new IllegalArgumentException("AgentMemoryId is required for persistent agent memory");
        }
        return id;
    }
}
