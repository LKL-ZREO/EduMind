package com.firedemo.demo.Service.ServiceImpl;

import com.firedemo.demo.Service.OpenClawService;
import com.firedemo.demo.config.properties.OpenClawProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import reactor.core.publisher.Flux;

import java.io.IOException;

/**
 * OpenClaw 服务实现 — 基于 Spring AI 2.0 ChatClient
 * <p>
 * Agent 路由通过 model 字段实现：model = "openclaw/<agentId>"
 * OpenClaw Gateway 的 /v1/chat/completions 端点需先启用
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OpenClawServiceImpl implements OpenClawService {

    private final ChatClient.Builder chatClientBuilder;
    private final OpenClawProperties agentRouting;

    @Override
    public String chat(String message, String status) {
        return chat(message, null, status);
    }

    @Override
    public String chat(String message, String sessionId, String status) {
        log.info("OpenClaw chat: sessionId={}, agent={}", sessionId, resolveAgent(status));
        return chatClientBuilder.build()
                .prompt()
                .user(message)
                .options(buildOptions(null, status))
                .call()
                .content();
    }

    @Override
    public Flux<String> streamChat(String message) {
        return streamChat(message, null);
    }

    @Override
    public Flux<String> streamChat(String message, String sessionId) {
        return streamChat(message, sessionId, null);
    }

    @Override
    public Flux<String> streamChat(String message, String sessionId, String status) {
        String agent = resolveAgent(status);
        log.info("OpenClaw stream: sessionId={}, agent={}, msg={}", sessionId, agent,
                message.substring(0, Math.min(50, message.length())));
        return chatClientBuilder.build()
                .prompt()
                .user(message)
                .options(buildOptions(sessionId, status))
                .stream()
                .content();
    }

    @Override
    public SseEmitter streamChatWithSse(String message) {
        return streamChatWithSse(message, null);
    }

    @Override
    public SseEmitter streamChatWithSse(String message, String sessionId) {
        return streamChatWithSse(message, sessionId, null);
    }

    @Override
    public SseEmitter streamChatWithSse(String message, String sessionId, Integer status) {
        SseEmitter emitter = new SseEmitter(120_000L);
        streamChat(message, sessionId, String.valueOf(status))
                .subscribe(
                        content -> {
                            try {
                                emitter.send(SseEmitter.event().data(content));
                            } catch (IOException e) {
                                log.error("SSE send failed", e);
                                emitter.completeWithError(e);
                            }
                        },
                        emitter::completeWithError,
                        emitter::complete
                );
        return emitter;
    }

    @Override
    public boolean checkConnection() {
        try {
            chatClientBuilder.build().prompt().user("hi").call().content();
            return true;
        } catch (Exception e) {
            log.warn("OpenClaw connection check failed: {}", e.getMessage());
            return false;
        }
    }

    /**
     * 根据 status 构建 ChatOptions，通过 model 字段路由到指定的 OpenClaw Agent
     */
    private OpenAiChatOptions buildOptions(String sessionId, String status) {
        return OpenAiChatOptions.builder()
                .model(resolveModel(status))
                .build();
    }

    private String resolveModel(String status) {
        return "openclaw/" + resolveAgent(status);
    }

    private String resolveAgent(String status) {
        if (status != null) {
            try {
                String agent = agentRouting.getMapping().get(Integer.parseInt(status));
                if (agent != null) {
                    return agent;
                }
            } catch (NumberFormatException ignored) {
            }
        }
        return agentRouting.getDefaultAgent();
    }
}
