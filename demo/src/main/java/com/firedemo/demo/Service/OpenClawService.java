package com.firedemo.demo.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import reactor.core.publisher.Flux;

/**
 * OpenClaw 服务接口
 */
public interface OpenClawService {

    /**
     * 非流式对话
     */
    String chat(String message);

    /**
     * 非流式对话（带会话ID）
     */
    String chat(String message, String sessionId);

    /**
     * 流式对话 - Flux 方式
     */
    Flux<String> streamChat(String message);

    /**
     * 流式对话 - Flux 方式（带会话ID）
     */
    Flux<String> streamChat(String message, String sessionId);

    /**
     * 流式对话 - SSE 方式
     */
    SseEmitter streamChatWithSse(String message);

    /**
     * 流式对话 - SSE 方式（带会话ID）
     */
    SseEmitter streamChatWithSse(String message, String sessionId);

    /**
     * 检查 OpenClaw 连接
     */
    boolean checkConnection();
}