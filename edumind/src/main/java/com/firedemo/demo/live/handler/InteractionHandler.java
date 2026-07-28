package com.firedemo.demo.live.handler;

import com.firedemo.demo.DTO.InteractionCreateDTO;
import com.firedemo.demo.DTO.StudentResponseDTO;
import com.firedemo.demo.Entity.ClassroomSession;
import com.firedemo.demo.live.service.InteractionService;
import com.firedemo.demo.live.service.LiveSessionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.stereotype.Controller;

import java.util.Map;

@Slf4j
@Controller
@RequiredArgsConstructor
public class InteractionHandler {

    private final InteractionService interactionService;
    private final LiveSessionService sessionService;

    @MessageMapping("/session/{sessionId}/interaction/create")
    public void create(@DestinationVariable Long sessionId, @Payload InteractionCreateDTO dto) {
        log.info("收到创建互动请求: sessionId={}, type={}, title={}", sessionId, dto.getType(), dto.getTitle());
        ClassroomSession session = sessionService.getSession(sessionId);
        if (session == null) { log.warn("课堂不存在: sessionId={}", sessionId); return; }
        interactionService.createAndActivate(sessionId, session.getTeacherId(), dto);
    }

    @MessageMapping("/session/{sessionId}/interaction/{interactionId}/close")
    public void close(@DestinationVariable Long sessionId, @DestinationVariable Long interactionId) {
        interactionService.closeInteraction(interactionId, sessionId);
    }

    @MessageMapping("/session/{sessionId}/interaction/{interactionId}/respond")
    public void respond(@DestinationVariable Long sessionId, @DestinationVariable Long interactionId,
                        @Payload StudentResponseDTO dto,
                        SimpMessageHeaderAccessor headerAccessor) {
        // 用课堂令牌身份覆盖 payload 中的 studentId/studentName，防止冒用
        String[] identity = resolveStudentIdentity(headerAccessor);
        dto.setStudentId(identity[0]);
        dto.setStudentName(identity[1]);
        log.info("收到学生作答: sessionId={}, interactionId={}, student={}, answer={}",
                sessionId, interactionId, dto.getStudentId(), dto.getAnswer());
        dto.setInteractionId(interactionId);
        interactionService.handleResponse(sessionId, dto);
    }

    /** 从 STOMP 会话属性读取学生身份。 */
    private static String[] resolveStudentIdentity(SimpMessageHeaderAccessor accessor) {
        Map<String, Object> attrs = accessor.getSessionAttributes();
        if (attrs == null) return new String[]{"unknown", "未知"};
        String studentId = String.valueOf(attrs.getOrDefault("studentId", "unknown"));
        String studentName = String.valueOf(attrs.getOrDefault("username", "未知"));
        return new String[]{studentId, studentName};
    }
}
