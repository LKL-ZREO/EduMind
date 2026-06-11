package com.firedemo.demo.Service;

import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import reactor.core.publisher.Flux;
import java.util.List;
import java.util.Map;

/**
 * OpenClaw 服务接口
 */
public interface OpenClawService {

    /** 非流式对话 */
    String chat(String message, String status);

    String chat(String message, String sessionId, String status);

    /** 流式对话（历史由 OpenClaw session 管理） */
    Flux<String> streamChat(String message);

    Flux<String> streamChat(String message, String sessionId);

    Flux<String> streamChat(String message, String sessionId, String status);

    /** 流式对话（带历史消息，用于维持上下文） */
    Flux<String> streamChat(String message, List<Map<String, Object>> history, String sessionId);

    /** SSE 流式 */
    SseEmitter streamChatWithSse(String message);

    SseEmitter streamChatWithSse(String message, String sessionId);

    SseEmitter streamChatWithSse(String message, String sessionId, Integer status);

    /** 健康检查 */
    boolean checkConnection();
}
