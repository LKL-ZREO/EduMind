package com.firedemo.demo.Service.ServiceImpl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.firedemo.demo.Service.OpenClawService;
import com.firedemo.demo.common.exception.BusinessException;
import com.firedemo.demo.common.exception.ErrorCode;
import com.firedemo.demo.config.properties.OpenClawProperties;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import reactor.core.publisher.Flux;

import java.io.IOException;
import java.net.http.HttpClient;
import java.time.Duration;
import java.util.*;

/**
 * OpenClaw 服务实现
 * <p>
 * 非流式：RestClient
 * 流式：WebClient SSE
 */
@Slf4j
@Service
public class OpenClawServiceImpl implements OpenClawService {

    private final OpenClawProperties agentRouting;
    private final String baseUrl;
    private final String apiKey;
    private final Double temperature;
    private final Duration timeout;
    private final ObjectMapper objectMapper;

    private final RestClient restClient;
    private final WebClient webClient;

    public OpenClawServiceImpl(OpenClawProperties agentRouting,
                               @Value("${langchain4j.openai.base-url}") String baseUrl,
                               @Value("${langchain4j.openai.api-key}") String apiKey,
                               @Value("${langchain4j.openai.temperature:0.2}") Double temperature,
                               @Value("${langchain4j.openai.timeout:300s}") Duration timeout,
                               ObjectMapper objectMapper) {
        this.agentRouting = agentRouting;
        this.baseUrl = baseUrl;
        this.apiKey = apiKey;
        this.temperature = temperature;
        this.timeout = timeout;
        this.objectMapper = objectMapper;

        HttpClient jdkClient = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1)
                .connectTimeout(Duration.ofSeconds(10))
                .build();

        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(jdkClient);
        factory.setReadTimeout(Duration.ofMinutes(5));

        this.restClient = RestClient.builder()
                .baseUrl(baseUrl)
                .defaultHeader("Authorization", "Bearer " + apiKey)
                .defaultHeader("Content-Type", MediaType.APPLICATION_JSON_VALUE)
                .requestFactory(factory)
                .build();

        this.webClient = WebClient.builder()
                .baseUrl(baseUrl)
                .defaultHeader("Authorization", "Bearer " + apiKey)
                .defaultHeader("Content-Type", MediaType.APPLICATION_JSON_VALUE)
                .build();
    }

    // ========================================================================
    //  非流式对话（RestClient，熔断保护）
    // ========================================================================

    @Override
    public String chat(String message, String status) {
        return chat(message, null, status);
    }

    @Override
    @SuppressWarnings("unchecked")
    @CircuitBreaker(name = "openclaw", fallbackMethod = "chatFallback")
    public String chat(String message, String sessionId, String status) {
        String agent = resolveAgent(status);
        log.info("OpenClaw chat: sessionId={}, agent={}", sessionId, agent);

        Map<String, Object> requestBody = buildRequestBody(message, null, agent, false);

        Map<String, Object> response = restClient.post()
                .uri("/chat/completions")
                .body(requestBody)
                .retrieve()
                .body(Map.class);

        if (response == null) throw new BusinessException(ErrorCode.AI_SERVICE_ERROR);

        List<Map<String, Object>> choices = (List<Map<String, Object>>) response.get("choices");
        if (choices == null || choices.isEmpty()) throw new BusinessException(ErrorCode.AI_SERVICE_ERROR);

        Map<String, Object> msg = (Map<String, Object>) choices.get(0).get("message");
        return (String) msg.get("content");
    }

    // ========================================================================
    //  流式对话（WebClient SSE，替换 LangChain4j）
    // ========================================================================

    @Override
    public Flux<String> streamChat(String message) {
        return streamChat(message, (String) null);
    }

    @Override
    public Flux<String> streamChat(String message, String sessionId) {
        return streamChat(message, sessionId, null);
    }

    @Override
    public Flux<String> streamChat(String message, String sessionId, String status) {
        String agent = resolveAgent(status);
        log.info("OpenClaw SSE stream: sessionId={}, agent={}, msg={}",
                sessionId, agent, truncate(message, 50));

        Map<String, Object> body = buildRequestBody(message, null, agent, true);
        return executeStreamRequest(body);
    }

    /**
     * 流式对话（带历史消息）
     */
    @Override
    @CircuitBreaker(name = "openclaw", fallbackMethod = "streamChatFallback")
    public Flux<String> streamChat(String message, List<Map<String, Object>> history, String sessionId) {
        String agent = resolveAgent(null);
        log.info("OpenClaw SSE stream with history: sessionId={}, agent={}, historyLen={}, msg={}",
                sessionId, agent, history != null ? history.size() : 0, truncate(message, 50));

        List<Map<String, String>> messages = new ArrayList<>();
        if (history != null) {
            for (Map<String, Object> h : history) {
                String role = (String) h.get("role");
                String content = (String) h.get("content");
                if (content != null) {
                    messages.add(Map.of("role", role, "content", content));
                }
            }
        }
        messages.add(Map.of("role", "user", "content", message));

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", "openclaw/" + agent);
        body.put("messages", messages);
        body.put("temperature", temperature);
        body.put("stream", true);

        return executeStreamRequest(body);
    }

    /**
     * 执行 SSE 流式请求，解析 OpenAI 格式
     * <pre>
     *   data: {"choices":[{"delta":{"content":"你好"}}]}
     *   data: [DONE]
     * </pre>
     */
    private Flux<String> executeStreamRequest(Map<String, Object> body) {
        return webClient.post()
                .uri("/chat/completions")
                .bodyValue(body)
                .accept(MediaType.TEXT_EVENT_STREAM)
                .retrieve()
                .bodyToFlux(String.class)
                .handle((String line, reactor.core.publisher.SynchronousSink<String> sink) -> {
                    if (line == null || line.isBlank()) {
                        return;
                    }
                    if (line.contains("[DONE]")) {
                        sink.complete();
                        return;
                    }
                    String content = parseDeltaContent(line);
                    if (content != null) {
                        sink.next(content);
                    }
                })
                .doOnError(e -> log.error("SSE stream error: {}", e.getMessage()));
    }

    /**
     * 解析 SSE data 行中的 content
     * <pre>
     *   "data: {\"choices\":[{\"delta\":{\"content\":\"Hello\"}}]}"  →  "Hello"
     *   "data: [DONE]"  →  null
     * </pre>
     */
    private String parseDeltaContent(String line) {
        try {
            // 去掉 "data: " 前缀
            String json = line;
            if (json.startsWith("data:")) {
                json = json.substring(5).trim();
            }
            if (json.isEmpty() || "[DONE]".equals(json)) {
                return null;
            }

            JsonNode root = objectMapper.readTree(json);
            JsonNode choices = root.get("choices");
            if (choices == null || choices.isEmpty()) return null;

            JsonNode delta = choices.get(0).get("delta");
            if (delta == null) return null;

            JsonNode content = delta.get("content");
            return content != null ? content.asText() : null;
        } catch (Exception e) {
            log.trace("Failed to parse SSE line: {}", line, e);
            return null;
        }
    }

    // ========================================================================
    //  SSE（Spring SseEmitter）
    // ========================================================================

    @Override
    public SseEmitter streamChatWithSse(String message) {
        return streamChatWithSse(message, null, null);
    }

    @Override
    public SseEmitter streamChatWithSse(String message, String sessionId) {
        return streamChatWithSse(message, sessionId, null);
    }

    @Override
    public SseEmitter streamChatWithSse(String message, String sessionId, Integer status) {
        SseEmitter emitter = new SseEmitter(300_000L);
        int s = status != null ? status : 0;
        streamChat(message, sessionId, String.valueOf(s))
                .subscribe(
                        content -> {
                            try {
                                emitter.send(SseEmitter.event().data(content));
                            } catch (IOException e) {
                                emitter.completeWithError(e);
                            }
                        },
                        emitter::completeWithError,
                        emitter::complete
                );
        return emitter;
    }

    // ========================================================================
    //  健康检查
    // ========================================================================

    @Override
    @SuppressWarnings("unchecked")
    public boolean checkConnection() {
        try {
            Map<String, Object> requestBody = Map.of(
                    "model", "openclaw/" + agentRouting.getDefaultAgent(),
                    "messages", List.of(Map.of("role", "user", "content", "hi")),
                    "temperature", 0.1,
                    "max_tokens", 5,
                    "stream", false
            );
            Map<String, Object> response = restClient.post()
                    .uri("/chat/completions")
                    .body(requestBody)
                    .retrieve()
                    .body(Map.class);
            return response != null && response.containsKey("choices");
        } catch (Exception e) {
            log.warn("OpenClaw connection check failed: {}", e.getMessage());
            return false;
        }
    }

    // ==================== 私有方法 ====================

    private Map<String, Object> buildRequestBody(String message, String sessionId,
                                                  String agent, boolean stream) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", "openclaw/" + agent);
        body.put("messages", List.of(Map.of("role", "user", "content", message)));
        body.put("temperature", temperature);
        if (stream) {
            body.put("stream", true);
        }
        if (sessionId != null && !sessionId.isEmpty()) {
            body.put("session_id", sessionId);
        }
        return body;
    }

    private String resolveAgent(String status) {
        if (status != null) {
            try {
                String agent = agentRouting.getMapping().get(Integer.parseInt(status));
                if (agent != null) return agent;
            } catch (NumberFormatException ignored) {}
        }
        return agentRouting.getDefaultAgent();
    }

    // ==================== 熔断 Fallback ====================

    /**
     * 非流式 chat 降级 — 熔断打开时抛出业务异常，由上游处理
     * <p>
     * 方法签名要求：与原始方法参数相同 + 末尾 Throwable
     */
    @SuppressWarnings("unused")
    private String chatFallback(String message, String sessionId, String status, Throwable t) {
        log.warn("AI 服务熔断/降级: sessionId={}, error={}", sessionId, t.getMessage());
        throw new BusinessException(ErrorCode.AI_SERVICE_ERROR);
    }

    /**
     * 流式 chat 降级 — 返回一条错误消息后结束流
     */
    @SuppressWarnings("unused")
    private Flux<String> streamChatFallback(String message, List<Map<String, Object>> history,
                                            String sessionId, Throwable t) {
        log.warn("AI 流式服务熔断/降级: sessionId={}, error={}", sessionId, t.getMessage());
        return Flux.just("AI 服务暂时不可用，请稍后重试。");
    }

    // ==================== 工具方法 ====================

    private String truncate(String text, int maxLen) {
        if (text == null) return null;
        return text.length() > maxLen ? text.substring(0, maxLen) + "…" : text;
    }
}
