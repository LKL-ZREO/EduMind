package com.firedemo.demo.agent.memory;

import dev.langchain4j.store.memory.chat.ChatMemoryStore;

import java.util.List;

public interface AgentChatMemoryStore extends ChatMemoryStore {

    int deleteByUserId(Long userId);

    List<AgentMemoryId> findByUserId(Long userId);
}
