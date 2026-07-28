package com.firedemo.demo.agent.langchain4j;

import dev.langchain4j.service.UserMessage;

/**
 * 无状态 Agent 接口 — 不绑定 @MemoryId，用于结构化输出场景（作业批改等），
 * 不需要会话记忆。
 */
public interface StatelessTeachingAgent {

    /**
     * 一次性对话，无记忆、无会话隔离。
     *
     * @param userMessage 用户消息
     * @return AI 回答
     */
    String chat(@UserMessage String userMessage);
}
