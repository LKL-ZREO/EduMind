package com.firedemo.edumind.live.handler;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Controller;

import java.util.Map;

@Slf4j
@Controller
@RequiredArgsConstructor
public class ReactionHandler {

    private final SimpMessagingTemplate messagingTemplate;

    @MessageMapping("/session/{sessionId}/reaction")
    public void react(@DestinationVariable Long sessionId, @Payload Map<String, String> body,
                      SimpMessageHeaderAccessor headerAccessor) {
        // 从课堂令牌身份读取，防止冒用
        String[] identity = resolveStudentIdentity(headerAccessor);
        String studentName = identity[1];
        String studentId = identity[0];
        String emoji = body.getOrDefault("emoji", "👍");
        String type = body.getOrDefault("type", "emoji"); // emoji 或 hand

        Map<String, Object> msg = new java.util.HashMap<>();
        msg.put("type", type);
        msg.put("emoji", emoji);
        msg.put("studentId", studentId);
        msg.put("studentName", studentName);
        msg.put("timestamp", System.currentTimeMillis());
        messagingTemplate.convertAndSend("/topic/session/" + sessionId + "/reactions", (Object) msg);
        log.debug("收到反馈: sessionId={}, student={}, emoji={}", sessionId, studentName, emoji);
    }

    /** 从 STOMP 会话属性读取学生身份。 */
    private static String[] resolveStudentIdentity(SimpMessageHeaderAccessor accessor) {
        Map<String, Object> attrs = accessor.getSessionAttributes();
        if (attrs == null) return new String[]{"unknown", "匿名"};
        String studentId = String.valueOf(attrs.getOrDefault("studentId", "unknown"));
        String studentName = String.valueOf(attrs.getOrDefault("username", "匿名"));
        return new String[]{studentId, studentName};
    }
}
