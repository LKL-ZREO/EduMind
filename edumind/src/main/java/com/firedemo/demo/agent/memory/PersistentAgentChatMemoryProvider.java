package com.firedemo.demo.agent.memory;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.firedemo.demo.config.properties.AgentMemoryProperties;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.memory.ChatMemory;
import dev.langchain4j.memory.chat.ChatMemoryProvider;
import dev.langchain4j.memory.chat.TokenWindowChatMemory;
import dev.langchain4j.model.TokenCountEstimator;
import dev.langchain4j.model.openai.OpenAiTokenCountEstimator;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

@Slf4j
@Component
public class PersistentAgentChatMemoryProvider implements ChatMemoryProvider {

    private final AgentChatMemoryStore memoryStore;
    private final int maxTokens;
    private final TokenCountEstimator tokenCountEstimator;
    private final Cache<AgentMemoryId, ChatMemory> memories;

    public PersistentAgentChatMemoryProvider(AgentChatMemoryStore memoryStore,
                                             AgentMemoryProperties properties) {
        this.memoryStore = memoryStore;
        if (properties.getMaxTokens() <= 0) {
            throw new IllegalArgumentException("edumind.agent.memory.max-tokens must be positive");
        }
        this.maxTokens = properties.getMaxTokens();
        this.tokenCountEstimator = new OpenAiTokenCountEstimator(properties.getTokenEstimatorModel());
        if (properties.getMaxActiveSessions() <= 0) {
            throw new IllegalArgumentException("edumind.agent.memory.max-active-sessions must be positive");
        }
        if (properties.getExpireAfterAccess() == null
                || properties.getExpireAfterAccess().isZero()
                || properties.getExpireAfterAccess().isNegative()) {
            throw new IllegalArgumentException("edumind.agent.memory.expire-after-access must be positive");
        }
        this.memories = Caffeine.newBuilder()
                .maximumSize(properties.getMaxActiveSessions())
                .expireAfterAccess(properties.getExpireAfterAccess())
                .build();
    }

    @Override
    public ChatMemory get(Object memoryId) {
        if (!(memoryId instanceof AgentMemoryId id)) {
            throw new IllegalArgumentException("AgentMemoryId is required for agent memory");
        }
        return memories.get(id, this::createMemory);
    }

    public void clearByUserId(Long userId) {
        Objects.requireNonNull(userId, "userId is required");
        memories.asMap().forEach((id, memory) -> {
            if (Objects.equals(id.userId(), userId)
                    && memories.asMap().remove(id, memory)) {
                memory.clear();
            }
        });
        int deleted = memoryStore.deleteByUserId(userId);
        log.info("Agent working memory cleared: userId={}, persistedSnapshots={}", userId, deleted);
    }

    /** Keeps persisted working memory aligned with a post-processed answer returned to the user. */
    public boolean replaceLastAiMessage(AgentMemoryId id, String expectedText, String replacementText) {
        Objects.requireNonNull(id, "memoryId is required");
        Objects.requireNonNull(replacementText, "replacementText is required");
        ChatMemory memory = get(id);
        List<ChatMessage> updated = new ArrayList<>(memory.messages());
        for (int i = updated.size() - 1; i >= 0; i--) {
            if (updated.get(i) instanceof AiMessage aiMessage
                    && Objects.equals(aiMessage.text(), expectedText)) {
                updated.set(i, AiMessage.from(replacementText));
                memory.set(updated);
                return true;
            }
        }
        log.warn("Agent memory answer was not replaced because the draft was not found: memoryId={}", id);
        return false;
    }

    int activeMemoryCount() {
        return Math.toIntExact(memories.estimatedSize());
    }

    private ChatMemory createMemory(AgentMemoryId id) {
        log.debug("Agent token-window memory created: memoryId={}, maxTokens={}", id, maxTokens);
        return TokenWindowChatMemory.builder()
                .id(id)
                .maxTokens(maxTokens, tokenCountEstimator)
                .chatMemoryStore(memoryStore)
                .alwaysKeepSystemMessageFirst(true)
                .build();
    }
}
