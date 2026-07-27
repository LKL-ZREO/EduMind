package com.firedemo.demo.agent.langchain4j;

import com.firedemo.demo.config.observability.HeliconeHeadersFactory;
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
    private final HeliconeHeadersFactory heliconeHeadersFactory;

    public LangChain4jConfig(LlmProperties llmProperties,
                             HeliconeHeadersFactory heliconeHeadersFactory) {
        llmProperties.validateForBuiltIn();
        this.llmProperties = llmProperties;
        this.heliconeHeadersFactory = heliconeHeadersFactory;
    }

    /**
     * 非流式 ChatLanguageModel（用于 Agent 循环和结构化输出）
     */
    @Bean
    public ChatModel chatLanguageModel() {
        String providerBaseUrl = llmProperties.getBaseUrl();
        log.info("创建 OpenAiChatModel: baseUrl={}, model={}, temperature={}, timeout={}, helicone={}",
                heliconeHeadersFactory.resolveBaseUrl(providerBaseUrl),
                llmProperties.resolveTextModel(), llmProperties.getTemperature(),
                llmProperties.getReadTimeout(), heliconeHeadersFactory.isEnabled());

        return OpenAiChatModel.builder()
                .baseUrl(heliconeHeadersFactory.resolveBaseUrl(providerBaseUrl))
                .apiKey(llmProperties.getApiKey())
                .customHeaders(heliconeHeadersFactory.create(providerBaseUrl, "text"))
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
        String providerBaseUrl = llmProperties.resolveVisionBaseUrl();
        log.info("Create vision OpenAiChatModel: baseUrl={}, model={}, temperature={}, timeout={}, helicone={}",
                heliconeHeadersFactory.resolveBaseUrl(providerBaseUrl),
                llmProperties.resolveVisionModel(), llmProperties.resolveVisionTemperature(),
                llmProperties.getReadTimeout(), heliconeHeadersFactory.isEnabled());

        return OpenAiChatModel.builder()
                .baseUrl(heliconeHeadersFactory.resolveBaseUrl(providerBaseUrl))
                .apiKey(llmProperties.resolveVisionApiKey())
                .customHeaders(heliconeHeadersFactory.create(providerBaseUrl, "vision"))
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
        String providerBaseUrl = llmProperties.getBaseUrl();
        log.info("创建 OpenAiStreamingChatModel: baseUrl={}, model={}, temperature={}, helicone={}",
                heliconeHeadersFactory.resolveBaseUrl(providerBaseUrl),
                llmProperties.resolveTextModel(), llmProperties.getTemperature(),
                heliconeHeadersFactory.isEnabled());

        return OpenAiStreamingChatModel.builder()
                .baseUrl(heliconeHeadersFactory.resolveBaseUrl(providerBaseUrl))
                .apiKey(llmProperties.getApiKey())
                .customHeaders(heliconeHeadersFactory.create(providerBaseUrl, "streaming"))
                .modelName(llmProperties.resolveTextModel())
                .temperature(llmProperties.getTemperature())
                .timeout(llmProperties.getReadTimeout())
                .logRequests(false)
                .logResponses(false)
                .build();
    }
}
