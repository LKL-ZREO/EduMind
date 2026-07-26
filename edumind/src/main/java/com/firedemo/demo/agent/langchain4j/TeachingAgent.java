package com.firedemo.demo.agent.langchain4j;

import dev.langchain4j.service.MemoryId;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.invocation.InvocationParameters;

/**
 * 非流式教学 Agent 接口 — 由 LangChain4j AiServices 生成动态代理。
 * <p>
 * System prompt 通过 {@code systemMessageProvider} 在 Builder 中动态注入（按课程变化），
 * 因此不在接口上使用 {@code @SystemMessage} 注解。
 */
public interface TeachingAgent {

    /**
     * 带会话记忆和工具访问的对话。
     *
     * @param memoryId    会话 ID（映射到 @MemoryId，用于 ChatMemory 隔离）
     * @param userMessage 用户消息
     * @return AI 回答（含工具调用结果）
     */
    String chat(@MemoryId String memoryId,
                @UserMessage String userMessage,
                InvocationParameters invocationParameters);
}
