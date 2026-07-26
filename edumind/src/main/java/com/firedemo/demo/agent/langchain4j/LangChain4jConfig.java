package com.firedemo.demo.agent.langchain4j;

import com.firedemo.demo.config.properties.LlmProperties;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.openai.OpenAiChatModel;
import dev.langchain4j.model.openai.OpenAiStreamingChatModel;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * LangChain4j 模型配置 — 手动创建 ChatLanguageModel / StreamingChatLanguageModel，
 * 不使用 Spring Boot Starter 的自动配置，保持对连接参数的完全控制。
 */
@Slf4j
@Configuration
@ConditionalOnProperty(name = "edumind.llm.backend", havingValue = "built-in", matchIfMissing = true)
public class LangChain4jConfig {

    private final LlmProperties llmProperties;

    public LangChain4jConfig(LlmProperties llmProperties) {
        this.llmProperties = llmProperties;
    }

    /**
     * 非流式 ChatLanguageModel（用于 Agent 循环和结构化输出）
     */
    @Bean
    public ChatModel chatLanguageModel() {
        log.info("创建 OpenAiChatModel: baseUrl={}, model={}, temperature={}, timeout={}",
                llmProperties.getBaseUrl(), llmProperties.resolveTextModel(),
                llmProperties.getTemperature(), llmProperties.getReadTimeout());

        return OpenAiChatModel.builder()
                .baseUrl(llmProperties.getBaseUrl())
                .apiKey(llmProperties.getApiKey())
                .modelName(llmProperties.resolveTextModel())
                .temperature(llmProperties.getTemperature())
                .timeout(llmProperties.getReadTimeout())
                .maxRetries(1)
                .logRequests(false)
                .logResponses(false)
                .build();
    }

    @Bean
    public ChatModel visionChatLanguageModel() {
        log.info("Create vision OpenAiChatModel: baseUrl={}, model={}, temperature={}, timeout={}",
                llmProperties.resolveVisionBaseUrl(), llmProperties.resolveVisionModel(),
                llmProperties.resolveVisionTemperature(), llmProperties.getReadTimeout());

        return OpenAiChatModel.builder()
                .baseUrl(llmProperties.resolveVisionBaseUrl())
                .apiKey(llmProperties.resolveVisionApiKey())
                .modelName(llmProperties.resolveVisionModel())
                .temperature(llmProperties.resolveVisionTemperature())
                .timeout(llmProperties.getReadTimeout())
                .maxRetries(1)
                .logRequests(false)
                .logResponses(false)
                .build();
    }

    /**
     * 流式 StreamingChatLanguageModel（用于 SSE 输出）
     */
    @Bean
    public StreamingChatModel streamingChatLanguageModel() {
        log.info("创建 OpenAiStreamingChatModel: baseUrl={}, model={}, temperature={}",
                llmProperties.getBaseUrl(), llmProperties.resolveTextModel(),
                llmProperties.getTemperature());

        return OpenAiStreamingChatModel.builder()
                .baseUrl(llmProperties.getBaseUrl())
                .apiKey(llmProperties.getApiKey())
                .modelName(llmProperties.resolveTextModel())
                .temperature(llmProperties.getTemperature())
                .timeout(llmProperties.getReadTimeout())
                .logRequests(false)
                .logResponses(false)
                .build();
    }
}
