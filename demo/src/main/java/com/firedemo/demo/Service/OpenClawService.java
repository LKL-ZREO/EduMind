package com.firedemo.demo.Service;

import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import reactor.core.publisher.Flux;
import java.util.List;
import java.util.Map;

/**
 * OpenClaw 服务接口
 */
public interface OpenClawService {

    // ==================== 原有方法（无 Tool） ====================

    String chat(String message, String status);

    String chat(String message, String sessionId, String status);

    Flux<String> streamChat(String message);

    Flux<String> streamChat(String message, String sessionId);

    Flux<String> streamChat(String message, String sessionId, String status);

    SseEmitter streamChatWithSse(String message);

    SseEmitter streamChatWithSse(String message, String sessionId);

    SseEmitter streamChatWithSse(String message, String sessionId, Integer status);

    boolean checkConnection();

    // ==================== 新增：支持 Tool Calling 的方法 ====================

    /**
     * 带 Tool 的非流式对话
     * @param message        用户消息
     * @param toolInstances  带 @Tool 注解的 Spring Bean 实例
     */
    String chatWithTools(String message, Object... toolInstances);

    /**
     * 带 Tool 和对话历史的非流式对话
     * @param message        用户消息
     * @param history        历史消息列表，[{role: "user/assistant", content: "..."}]
     * @param toolInstances  带 @Tool 注解的 Spring Bean 实例
     */
    String chatWithTools(String message, List<Map<String, Object>> history, Object... toolInstances);

    /**
     * 带 Tool 的流式对话（Flux）
     */
    Flux<String> streamChatWithTools(String message, Object... toolInstances);

    /**
     * 带 Tool 和对话历史的流式对话（Flux）
     */
    Flux<String> streamChatWithTools(String message, List<Map<String, Object>> history, Object... toolInstances);

    /**
     * 带 Tool 的 SSE 流式对话
     */
    SseEmitter streamChatWithSseAndTools(String message, Object... toolInstances);

    /**
     * 带 Tool 和对话历史的 SSE 流式对话
     */
    SseEmitter streamChatWithSseAndTools(String message, List<Map<String, Object>> history, Object... toolInstances);
}
