package com.firedemo.demo.live.handler;

import com.firedemo.demo.live.service.HandRaiseService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.stereotype.Controller;

import java.util.Map;

/**
 * 举手队列 WebSocket 端点。
 * 学生身份从 STOMP 会话属性读取（CONNECT 阶段 JWT 验证后存储），防止冒用。
 */
@Slf4j
@Controller
@RequiredArgsConstructor
public class HandRaiseHandler {

    private final HandRaiseService handRaiseService;

    /** 学生举手 */
    @MessageMapping("/session/{sessionId}/hand/raise")
    public void raise(@DestinationVariable Long sessionId,
                      SimpMessageHeaderAccessor headerAccessor) {
        String[] identity = resolveStudentIdentity(headerAccessor);
        handRaiseService.raise(sessionId, identity[0], identity[1]);
    }

    /** 学生取消举手 */
    @MessageMapping("/session/{sessionId}/hand/lower")
    public void lower(@DestinationVariable Long sessionId,
                      SimpMessageHeaderAccessor headerAccessor) {
        String[] identity = resolveStudentIdentity(headerAccessor);
        handRaiseService.lower(sessionId, identity[0]);
    }

    /** 教师点名（body 中可选指定 studentId，不指定则 FIFO 队首） */
    @MessageMapping("/session/{sessionId}/hand/call")
    public void call(@DestinationVariable Long sessionId,
                     @Payload(required = false) Map<String, String> body) {
        String studentId = body != null ? body.get("studentId") : null;
        handRaiseService.call(sessionId, studentId);
    }

    /** 教师移除学生举手 */
    @MessageMapping("/session/{sessionId}/hand/dismiss")
    public void dismiss(@DestinationVariable Long sessionId,
                        @Payload Map<String, String> body) {
        String studentId = body != null ? body.get("studentId") : null;
        if (studentId != null && !studentId.isEmpty()) {
            handRaiseService.dismiss(sessionId, studentId);
        }
    }

    /** 从 STOMP 会话属性读取学生身份（CONNECT 阶段 JWT 验证后由 WebSocketAuthInterceptor 存储） */
    private static String[] resolveStudentIdentity(SimpMessageHeaderAccessor accessor) {
        Map<String, Object> attrs = accessor.getSessionAttributes();
        if (attrs == null) return new String[]{"unknown", "匿名"};
        String studentId = String.valueOf(attrs.getOrDefault("userId", "unknown"));
        String studentName = String.valueOf(attrs.getOrDefault("username", "匿名"));
        return new String[]{studentId, studentName};
    }
}
