package com.firedemo.demo.Service.ServiceImpl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.firedemo.demo.Bean.OpenClawProperties;
import com.firedemo.demo.DTO.OpenResponsesResponse;
import com.firedemo.demo.DTO.StreamChunk;
import com.firedemo.demo.Service.OpenClawService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;

import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import reactor.core.publisher.Flux;

import java.io.IOException;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class OpenClawServiceImpl implements OpenClawService {

    private final WebClient openClawWebClient;
    private final OpenClawProperties properties;
    private final ObjectMapper objectMapper;
    /**
     * 构建 OpenResponses 请求体（带会话ID）
     */
    private Map<String, Object> buildRequest(String message, boolean stream, String sessionId) {
        java.util.Map<String, Object> request = new java.util.HashMap<>();
        request.put("model", "openclaw");
        request.put("input", message);
        request.put("stream", stream);
        if (sessionId != null && !sessionId.isEmpty()) {
            request.put("user", sessionId);
        }
        return request;
    }

    /**
     * 从响应中提取文本内容
     */
    private String extractContent(OpenResponsesResponse response) {
        if (response.getError() != null) {
            throw new RuntimeException("OpenClaw API Error: " + response.getError().getMessage());
        }

        if (response.getOutput() == null || response.getOutput().isEmpty()) {
            return "";
        }

        StringBuilder result = new StringBuilder();
        for (OpenResponsesResponse.OutputItem item : response.getOutput()) {
            if ("message".equals(item.getType()) && item.getContent() != null) {
                // content 可能是 List<ContentPart> 或 String
                if (item.getContent() instanceof List) {
                    List<?> contentList = (List<?>) item.getContent();
                    for (Object part : contentList) {
                        if (part instanceof Map) {
                            Map<?, ?> partMap = (Map<?, ?>) part;
                            if ("output_text".equals(partMap.get("type"))) {
                                result.append(partMap.get("text"));
                            }
                        }
                    }
                } else if (item.getContent() instanceof String) {
                    result.append(item.getContent());
                }
            }
        }
        return result.toString();
    }

    @Override
    public String chat(String message) {
        return chat(message, null);
    }

    @Override
    public String chat(String message, String sessionId) {
        Map<String, Object> requestBody = buildRequest(message, false, sessionId);

        // 构建WebClient请求，添加session key头
        var requestSpec = openClawWebClient.post()
                .uri("/v1/responses")
                .contentType(MediaType.APPLICATION_JSON)
                .header("x-openclaw-agent-id", properties.getAgent());

        // 如果有sessionId，添加x-openclaw-session-key头
        if (sessionId != null && !sessionId.isEmpty()) {
            requestSpec = requestSpec.header("x-openclaw-session-key", sessionId);
        }

        return requestSpec
                .bodyValue(requestBody)
                .retrieve()
                .bodyToMono(OpenResponsesResponse.class)
                .map(this::extractContent)
                .doOnError(e -> log.error("调用 OpenClaw 失败", e))
                .onErrorMap(e -> new RuntimeException("AI 服务暂时不可用，请稍后重试", e))
                .block();
    }

    @Override
    public Flux<String> streamChat(String message) {
        return streamChat(message, null);
    }

    @Override
    public Flux<String> streamChat(String message, String sessionId) {
        Map<String, Object> requestBody = buildRequest(message, true, sessionId);
        
        log.info("OpenClaw请求: user={}, message={}", sessionId, message.substring(0, Math.min(50, message.length())));
        log.info("请求头: agent={}, session={}", properties.getAgent(), sessionId);
        // 构建WebClient请求，添加session key头
        var requestSpec = openClawWebClient.post()
                .uri("/v1/responses")
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.TEXT_EVENT_STREAM)
                .header("x-openclaw-agent-id", "jarvis");;
//        // 如果有sessionId，添加x-openclaw-session-key头
//        if (sessionId != null && !sessionId.isEmpty()) {
//            requestSpec = requestSpec.header("x-openclaw-session-key",
//                                            sessionId);
//
//        }
        
        return requestSpec
                .bodyValue(requestBody)
                .retrieve()
                .bodyToFlux(String.class)
                .doOnNext(line -> log.info("WebClient收到行: {}", line))
                .flatMap(this::parseSseLine)
                .doOnError(e -> log.error("流式调用失败", e));
    }

    @Override
    public SseEmitter streamChatWithSse(String message) {
        return streamChatWithSse(message, null);
    }

    @Override
    public SseEmitter streamChatWithSse(String message, String sessionId) {
        SseEmitter emitter = new SseEmitter(120000L);

        streamChat(message, sessionId)
                .subscribe(
                        content -> {
                            try {
                                StreamChunk chunk = StreamChunk.builder()
                                        .content(content)
                                        .done(false)
                                        .build();
                                emitter.send(SseEmitter.event()
                                        .data(objectMapper.writeValueAsString(chunk)));
                            } catch (IOException e) {
                                log.error("发送 SSE 失败", e);
                                emitter.completeWithError(e);
                            }
                        },
                        error -> {
                            log.error("SSE 流错误", error);
                            emitter.completeWithError(error);
                        },
                        emitter::complete
                );

        return emitter;
    }

    @Override
    public boolean checkConnection() {
        return openClawWebClient.get()
                .uri("/health")
                .retrieve()
                .toBodilessEntity()
                .map(response -> response.getStatusCode().is2xxSuccessful())
                .onErrorReturn(false)
                .block();
    }

    /**
     * 解析 OpenClaw 返回的 JSON 行
     */
    private Flux<String> parseSseLine(String line) {
        log.debug("解析行: {}", line);
        
        if (line == null || line.isEmpty() || "[DONE]".equals(line)) {
            return Flux.empty();
        }

        try {
            Map<?, ?> event = objectMapper.readValue(line, Map.class);
            String eventType = (String) event.get("type");

            if ("response.output_text.delta".equals(eventType)) {
                String delta = (String) event.get("delta");
                if (delta != null) {
                    log.debug("提取到delta: {}", delta);
                    return Flux.just(delta);
                }
            }
        } catch (Exception e) {
            log.warn("解析事件失败: {}", line, e);
        }
        return Flux.empty();
    }
}
