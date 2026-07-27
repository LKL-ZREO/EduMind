package com.firedemo.demo.agent.memory;

import com.firedemo.demo.mapper.AgentChatMemoryMapper;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.ChatMessageDeserializer;
import dev.langchain4j.data.message.ChatMessageSerializer;
import dev.langchain4j.data.message.UserMessage;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PostgresAgentChatMemoryStoreTest {

    private final AgentChatMemoryMapper mapper = mock(AgentChatMemoryMapper.class);
    private final PostgresAgentChatMemoryStore store = new PostgresAgentChatMemoryStore(mapper);

    @Test
    void deserializesAStoredConversationSnapshot() {
        AgentMemoryId id = new AgentMemoryId(42L, "session-1");
        List<ChatMessage> expected = List.of(
                UserMessage.from("question"),
                AiMessage.from("answer"));
        when(mapper.selectMessagesJson(id.storageKey()))
                .thenReturn(ChatMessageSerializer.messagesToJson(expected));

        List<ChatMessage> restored = store.getMessages(id);

        assertThat(restored).hasSize(2);
        assertThat(((UserMessage) restored.get(0)).singleText()).isEqualTo("question");
        assertThat(((AiMessage) restored.get(1)).text()).isEqualTo("answer");
    }

    @Test
    void serializesAndUpsertsTheEntireWindow() {
        AgentMemoryId id = new AgentMemoryId(42L, "session-1");
        List<ChatMessage> messages = List.of(UserMessage.from("remember this"));
        ArgumentCaptor<String> json = ArgumentCaptor.forClass(String.class);

        store.updateMessages(id, messages);

        verify(mapper).upsert(eq(id.storageKey()), eq(42L), eq("session-1"), json.capture());
        assertThat(ChatMessageDeserializer.messagesFromJson(json.getValue())).hasSize(1);
    }

    @Test
    void rejectsAnUnscopedRawSessionId() {
        assertThatThrownBy(() -> store.updateMessages("session-1", List.of()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("AgentMemoryId");
        verify(mapper, org.mockito.Mockito.never()).upsert(any(), any(), any(), any());
    }
}
