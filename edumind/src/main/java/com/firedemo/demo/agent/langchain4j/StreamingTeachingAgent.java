package com.firedemo.demo.agent.langchain4j;

import dev.langchain4j.service.MemoryId;
import dev.langchain4j.service.TokenStream;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.invocation.InvocationParameters;

/**
 * 流式教学 Agent 接口 — 返回 {@link TokenStream}，由调用方转为 {@code Flux<String>}。
 * <p>
 * 流式路径不做自我反思——TokenStream 已经开始输出，无法事后修正。
 */
public interface StreamingTeachingAgent {

    /**
     * 流式对话（带会话记忆和工具访问）。
     *
     * @param memoryId    会话 ID
     * @param userMessage 用户消息
     * @return TokenStream，调用方按需转为 SSE / Flux
     */
    TokenStream chat(@MemoryId String memoryId,
                     @UserMessage String userMessage,
                     InvocationParameters invocationParameters);
}
