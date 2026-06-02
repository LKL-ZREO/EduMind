package com.firedemo.demo.Service.ServiceImpl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.firedemo.demo.Service.OpenClawService;
import com.firedemo.demo.config.properties.OpenClawProperties;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.agent.tool.ToolSpecifications;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.chat.response.StreamingChatResponseHandler;
import dev.langchain4j.model.openai.OpenAiStreamingChatModel;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import reactor.core.publisher.Flux;

import java.io.IOException;
import java.lang.reflect.Method;
import java.net.http.HttpClient;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * OpenClaw 服务实现
 * <p>
 * 非流式：RestClient（chat + chatWithTools 都用 RestClient）
 * 流式：LangChain4j OpenAiStreamingChatModel
 * Tool Calling：RestClient 手工实现 tool_calls 循环（绕过 OpenAiChatModel JDK HttpClient bug）
 */
@Slf4j
@Service
public class OpenClawServiceImpl implements OpenClawService {

    private final OpenClawProperties agentRouting;
    private final String baseUrl;
    private final String apiKey;
    private final Double temperature;
    private final Duration timeout;

    /** 非流式 RestClient */
    private final RestClient restClient;
    private final ObjectMapper objectMapper = new ObjectMapper();

    /** 流式 StreamingChatModel */
    private final Map<String, StreamingChatModel> streamingCache = new ConcurrentHashMap<>();

    /** 最大 tool_calls 循环次数 */
    private static final int MAX_TOOL_ROUNDS = 10;


    public OpenClawServiceImpl(OpenClawProperties agentRouting,
                               @Value("${langchain4j.openai.base-url}") String baseUrl,
                               @Value("${langchain4j.openai.api-key}") String apiKey,
                               @Value("${langchain4j.openai.temperature:0.2}") Double temperature,
                               @Value("${langchain4j.openai.timeout:300s}") Duration timeout) {
        this.agentRouting = agentRouting;
        this.baseUrl = baseUrl;
        this.apiKey = apiKey;
        this.temperature = temperature;
        this.timeout = timeout;

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
    }


    // ========================================================================
    //  非流式对话（RestClient，无 Tool）
    // ========================================================================

    @Override
    public String chat(String message, String status) {
        return chat(message, null, status);
    }

    @Override
    @SuppressWarnings("unchecked")
    public String chat(String message, String sessionId, String status) {
        String agent = resolveAgent(status);
        log.info("OpenClaw chat: sessionId={}, agent={}", sessionId, agent);

        Map<String, Object> requestBody = Map.of(
                "model", "openclaw/" + agent,
                "messages", List.of(Map.of("role", "user", "content", message)),
                "temperature", temperature,
                "stream", false
        );

        Map<String, Object> response = restClient.post()
                .uri("/chat/completions")
                .body(requestBody)
                .retrieve()
                .body(Map.class);

        if (response == null) {
            throw new RuntimeException("Gateway returned null response");
        }

        List<Map<String, Object>> choices = (List<Map<String, Object>>) response.get("choices");
        if (choices == null || choices.isEmpty()) {
            throw new RuntimeException("Gateway returned empty choices");
        }

        Map<String, Object> msg = (Map<String, Object>) choices.get(0).get("message");
        return (String) msg.get("content");
    }


    // ========================================================================
    //  支持 Tool Calling 的非流式对话（RestClient 手工实现 tool_calls 循环）
    // ========================================================================

    @Override
    @SuppressWarnings("unchecked")
    public String chatWithTools(String message, Object... toolInstances) {
        List<Map<String, Object>> messages = new ArrayList<>();
        messages.add(Map.of("role", "user", "content", message));
        return doChatWithTools(messages, toolInstances);
    }

    @Override
    @SuppressWarnings("unchecked")
    public String chatWithTools(String message, List<Map<String, Object>> history, Object... toolInstances) {
        List<Map<String, Object>> messages = new ArrayList<>();
        if (history != null) messages.addAll(history);
        messages.add(Map.of("role", "user", "content", message));
        return doChatWithTools(messages, toolInstances);
    }

    @SuppressWarnings("unchecked")
    private String doChatWithTools(List<Map<String, Object>> messages, Object... toolInstances) {
        String agent = resolveAgent(null);
        String lastMsg = messages.isEmpty() ? "" : (String) messages.get(messages.size() - 1).get("content");
        log.info("doChatWithTools: agent={}, msg={}, historyLen={}", agent, truncate(lastMsg, 60), messages.size());

        // 从 tool 实例中提取 ToolSpecification，转为 OpenAI tools 格式
        List<Map<String, Object>> toolsList = new ArrayList<>();
        for (Object tool : toolInstances) {
            List<ToolSpecification> specs = ToolSpecifications.toolSpecificationsFrom(tool);
            for (ToolSpecification spec : specs) {
                toolsList.add(Map.of(
                        "type", "function",
                        "function", Map.of(
                                "name", spec.name(),
                                "description", spec.description() != null ? spec.description() : "",
                                "parameters", spec.parameters() != null
                                        ? spec.parameters() : Map.of("type", "object", "properties", Map.of())
                        )
                ));
            }
        }

        for (int round = 0; round < MAX_TOOL_ROUNDS; round++) {
            Map<String, Object> requestBody = new LinkedHashMap<>();
            requestBody.put("model", "openclaw/" + agent);
            requestBody.put("messages", messages);
            requestBody.put("temperature", temperature);
            requestBody.put("stream", false);
            if (!toolsList.isEmpty()) {
                requestBody.put("tools", toolsList);
                requestBody.put("tool_choice", "auto");
            }

            Map<String, Object> response = restClient.post()
                    .uri("/chat/completions")
                    .body(requestBody)
                    .retrieve()
                    .body(Map.class);

            if (response == null) throw new RuntimeException("Gateway returned null");

            List<Map<String, Object>> choices = (List<Map<String, Object>>) response.get("choices");
            if (choices == null || choices.isEmpty())
                throw new RuntimeException("Gateway returned empty choices");

            Map<String, Object> choice = choices.get(0);
            Map<String, Object> msg = (Map<String, Object>) choice.get("message");
            String finishReason = (String) choice.get("finish_reason");

            if (!"tool_calls".equals(finishReason)) {
                return (String) msg.get("content");
            }

            messages.add(msg);

            List<Map<String, Object>> toolCalls = (List<Map<String, Object>>) msg.get("tool_calls");
            if (toolCalls == null) return (String) msg.get("content");

            for (Map<String, Object> tc : toolCalls) {
                Map<String, Object> func = (Map<String, Object>) tc.get("function");
                String name = (String) func.get("name");
                String argsJson = (String) func.get("arguments");
                String toolCallId = (String) tc.get("id");

                log.info("⚡ Tool call: name={}, args={}", name, argsJson);

                ToolExecutionRequest ter = ToolExecutionRequest.builder()
                        .name(name).arguments(argsJson).build();
                String result = executeTool(toolInstances, ter);
                log.info("✅ Tool result: name={}, resultLen={}", name, result != null ? result.length() : 0);

                messages.add(Map.of(
                        "role", "tool",
                        "tool_call_id", toolCallId,
                        "content", result != null ? result : "ok"
                ));
            }
        }

        log.warn("Tool calling exceeded max rounds ({})", MAX_TOOL_ROUNDS);
        return "工具调用次数过多，请简化问题";
    }

    @Override
    public Flux<String> streamChatWithTools(String message, List<Map<String, Object>> history, Object... toolInstances) {
        return Flux.create(emitter -> {
            try {
                List<Map<String, Object>> messages = new ArrayList<>();
                if (history != null) messages.addAll(history);
                messages.add(Map.of("role", "user", "content", message));
                String result = doChatWithTools(messages, toolInstances);
                emitter.next(result);
                emitter.complete();
            } catch (Exception e) {
                emitter.error(e);
            }
        });
    }

    @Override
    public SseEmitter streamChatWithSseAndTools(String message, List<Map<String, Object>> history, Object... toolInstances) {
        SseEmitter emitter = new SseEmitter(120_000L);
        streamChatWithTools(message, history, toolInstances)
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
    //  流式（Flux）
    // ========================================================================

    @Override
    public Flux<String> streamChat(String message) {
        return streamChat(message, null, null);
    }

    @Override
    public Flux<String> streamChat(String message, String sessionId) {
        return streamChat(message, sessionId, null);
    }

    @Override
    public Flux<String> streamChat(String message, String sessionId, String status) {
        String agent = resolveAgent(status);
        log.info("OpenClaw stream: sessionId={}, agent={}, msg={}",
                sessionId, agent, truncate(message, 50));
        StreamingChatModel model = getStreamingModel(agent);

        return Flux.create(emitter -> {
            model.chat(message, new StreamingChatResponseHandler() {
                @Override
                public void onPartialResponse(String partialResponse) {
                    emitter.next(partialResponse);
                }

                @Override
                public void onCompleteResponse(ChatResponse completeResponse) {
                    emitter.complete();
                }

                @Override
                public void onError(Throwable error) {
                    emitter.error(error);
                }
            });
        });
    }


    // ========================================================================
    //  带 Tool 的流式（内部调 chatWithTools 全量返回）
    // ========================================================================

    @Override
    public Flux<String> streamChatWithTools(String message, Object... toolInstances) {
        return Flux.create(emitter -> {
            try {
                String result = chatWithTools(message, toolInstances);
                emitter.next(result);
                emitter.complete();
            } catch (Exception e) {
                emitter.error(e);
            }
        });
    }


    // ========================================================================
    //  SSE
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
    public SseEmitter streamChatWithSseAndTools(String message, Object... toolInstances) {
        SseEmitter emitter = new SseEmitter(120_000L);
        streamChatWithTools(message, toolInstances)
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


    // ==================== 健康检查 ====================

    @Override
    public boolean checkConnection() {
        try {
            chat("hi", null, null);
            return true;
        } catch (Exception e) {
            log.warn("OpenClaw connection check failed: {}", e.getMessage());
            return false;
        }
    }


    // ==================== 私有方法 ====================

    private StreamingChatModel getStreamingModel(String agent) {
        return streamingCache.computeIfAbsent(agent, a ->
                OpenAiStreamingChatModel.builder()
                        .baseUrl(baseUrl)
                        .apiKey(apiKey)
                        .modelName("openclaw/" + a)
                        .temperature(temperature)
                        .timeout(timeout)
                        .build()
        );
    }

    private String resolveAgent(String status) {
        if (status != null) {
            try {
                String agent = agentRouting.getMapping().get(Integer.parseInt(status));
                if (agent != null) return agent;
            } catch (NumberFormatException ignored) {
            }
        }
        return agentRouting.getDefaultAgent();
    }

    private String truncate(String text, int maxLen) {
        if (text == null) return null;
        return text.length() > maxLen ? text.substring(0, maxLen) + "…" : text;
    }

    /**
     * 根据 ToolExecutionRequest 找到 @Tool 方法并通过 Jackson 解析参数后反射调用
     */
    @SuppressWarnings("unchecked")
    private String executeTool(Object[] toolInstances, ToolExecutionRequest request) {
        String name = request.name();
        String argumentsJson = request.arguments();

        for (Object toolInstance : toolInstances) {
            for (Method method : toolInstance.getClass().getMethods()) {
                if (!method.isAnnotationPresent(dev.langchain4j.agent.tool.Tool.class)) continue;

                String toolName = method.getAnnotation(dev.langchain4j.agent.tool.Tool.class).name();
                if (toolName.isEmpty()) toolName = method.getName();
                if (!toolName.equals(name)) continue;

                try {
                    // 用 Jackson 解析参数
                    Map<String, Object> argsMap = objectMapper.readValue(argumentsJson,
                            new com.fasterxml.jackson.core.type.TypeReference<Map<String, Object>>() {});
                    java.lang.reflect.Parameter[] params = method.getParameters();
                    Object[] values = new Object[params.length];

                    for (int i = 0; i < params.length; i++) {
                        Object val = argsMap.get(params[i].getName());
                        Class<?> type = params[i].getType();
                        if (val == null) { values[i] = null; }
                        else if (type == String.class) { values[i] = val.toString(); }
                        else if (type == int.class || type == Integer.class) { values[i] = val instanceof Number ? ((Number) val).intValue() : Integer.parseInt(val.toString()); }
                        else if (type == long.class || type == Long.class) { values[i] = val instanceof Number ? ((Number) val).longValue() : Long.parseLong(val.toString()); }
                        else if (type == double.class || type == Double.class) { values[i] = val instanceof Number ? ((Number) val).doubleValue() : Double.parseDouble(val.toString()); }
                        else if (type == boolean.class || type == Boolean.class) { values[i] = val instanceof Boolean ? val : Boolean.parseBoolean(val.toString()); }
                        else { values[i] = val; }
                    }

                    Object result = method.invoke(toolInstance, values);
                    return result != null ? result.toString() : "ok";
                } catch (Exception e) {
                    log.error("Tool execution failed: name={}", name, e);
                    return "工具执行失败: " + e.getMessage();
                }
            }
        }
        return "未找到工具: " + name;
    }
}
