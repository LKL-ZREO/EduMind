package com.firedemo.demo.agent.memory;

import com.firedemo.demo.config.properties.AgentMemoryProperties;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.memory.ChatMemory;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

import static org.assertj.core.api.Assertions.assertThat;

class PersistentAgentChatMemoryProviderTest {

    @Test
    void sharesOneMemoryBetweenAgentProxiesAndRestoresItAfterProviderRecreation() {
        InMemoryStore store = new InMemoryStore();
        AgentMemoryProperties properties = properties(500);
        AgentMemoryId id = new AgentMemoryId(42L, "session-1");
        PersistentAgentChatMemoryProvider firstProvider =
                new PersistentAgentChatMemoryProvider(store, properties);

        ChatMemory first = firstProvider.get(id);
        first.add(UserMessage.from("persistent question"));
        first.add(AiMessage.from("persistent answer"));

        assertThat(firstProvider.get(id)).isSameAs(first);
        PersistentAgentChatMemoryProvider recreatedProvider =
                new PersistentAgentChatMemoryProvider(store, properties);
        assertThat(recreatedProvider.get(id).messages()).hasSize(2);
    }

    @Test
    void boundsWorkingMemoryByTokensInsteadOfAnArbitraryMessageCount() {
        InMemoryStore store = new InMemoryStore();
        PersistentAgentChatMemoryProvider provider =
                new PersistentAgentChatMemoryProvider(store, properties(80));
        AgentMemoryId id = new AgentMemoryId(42L, "session-1");
        ChatMemory memory = provider.get(id);

        for (int i = 0; i < 20; i++) {
            memory.add(UserMessage.from("question number " + i + " with a few useful words"));
            memory.add(AiMessage.from("answer number " + i + " with a few useful words"));
        }

        assertThat(memory.messages()).isNotEmpty().hasSizeLessThan(40);
        assertThat(store.getMessages(id)).hasSameSizeAs(memory.messages());
    }

    @Test
    void clearsOnlyTheRequestedUsersWorkingMemory() {
        InMemoryStore store = new InMemoryStore();
        PersistentAgentChatMemoryProvider provider =
                new PersistentAgentChatMemoryProvider(store, properties(500));
        AgentMemoryId firstUser = new AgentMemoryId(42L, "session-1");
        AgentMemoryId secondUser = new AgentMemoryId(84L, "session-1");
        provider.get(firstUser).add(UserMessage.from("first"));
        provider.get(secondUser).add(UserMessage.from("second"));

        provider.clearByUserId(42L);

        assertThat(store.getMessages(firstUser)).isEmpty();
        assertThat(store.getMessages(secondUser)).hasSize(1);
        assertThat(provider.activeMemoryCount()).isEqualTo(1);
    }

    @Test
    void replacesThePersistedDraftWithThePostProcessedAnswer() {
        InMemoryStore store = new InMemoryStore();
        PersistentAgentChatMemoryProvider provider =
                new PersistentAgentChatMemoryProvider(store, properties(500));
        AgentMemoryId id = new AgentMemoryId(42L, "session-1");
        provider.get(id).add(UserMessage.from("question"));
        provider.get(id).add(AiMessage.from("draft answer"));

        boolean replaced = provider.replaceLastAiMessage(id, "draft answer", "refined answer");

        assertThat(replaced).isTrue();
        List<ChatMessage> persisted = store.getMessages(id);
        assertThat(((AiMessage) persisted.get(1)).text()).isEqualTo("refined answer");
    }

    private AgentMemoryProperties properties(int maxTokens) {
        AgentMemoryProperties properties = new AgentMemoryProperties();
        properties.setMaxTokens(maxTokens);
        properties.setTokenEstimatorModel("gpt-4o-mini");
        return properties;
    }

    private static final class InMemoryStore implements AgentChatMemoryStore {

        private final Map<AgentMemoryId, List<ChatMessage>> messages = new ConcurrentHashMap<>();

        @Override
        public List<ChatMessage> getMessages(Object memoryId) {
            return messages.getOrDefault(requireId(memoryId), List.of());
        }

        @Override
        public void updateMessages(Object memoryId, List<ChatMessage> updatedMessages) {
            messages.put(requireId(memoryId), List.copyOf(updatedMessages));
        }

        @Override
        public void deleteMessages(Object memoryId) {
            messages.remove(requireId(memoryId));
        }

        @Override
        public int deleteByUserId(Long userId) {
            int before = messages.size();
            messages.keySet().removeIf(id -> Objects.equals(id.userId(), userId));
            return before - messages.size();
        }

        @Override
        public List<AgentMemoryId> findByUserId(Long userId) {
            return messages.keySet().stream()
                    .filter(id -> Objects.equals(id.userId(), userId))
                    .toList();
        }

        private AgentMemoryId requireId(Object memoryId) {
            return (AgentMemoryId) memoryId;
        }
    }
}
