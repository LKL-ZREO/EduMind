package com.firedemo.edumind.live.service;

import com.firedemo.edumind.live.ClassroomSession;
import com.firedemo.edumind.live.security.LiveSessionTokenService;
import com.firedemo.edumind.classroom.ClassInfoMapper;
import com.firedemo.edumind.classroom.ClassStudentMapper;
import com.firedemo.edumind.live.ClassroomSessionMapper;
import com.firedemo.edumind.auth.UserMapper;
import org.junit.jupiter.api.Test;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class LiveSessionAutoEndTest {

    @Test
    void autoEndClosesActiveQuestionAndClearsTransientClassroomState() {
        ClassroomSessionMapper sessionMapper = mock(ClassroomSessionMapper.class);
        InteractionService interactionService = mock(InteractionService.class);
        LiveNotificationService notificationService = mock(LiveNotificationService.class);
        HandRaiseService handRaiseService = mock(HandRaiseService.class);
        StudentPresenceService presenceService = mock(StudentPresenceService.class);
        SimpMessagingTemplate messagingTemplate = mock(SimpMessagingTemplate.class);
        LiveSessionService service = new LiveSessionService(
                sessionMapper,
                mock(ClassInfoMapper.class),
                mock(ClassStudentMapper.class),
                interactionService,
                mock(UserMapper.class),
                mock(LiveSessionTokenService.class),
                notificationService,
                handRaiseService,
                presenceService,
                messagingTemplate);
        ClassroomSession session = ClassroomSession.builder()
                .id(99L).teacherId(5L).classId(10L).status("ACTIVE").build();
        when(sessionMapper.selectById(99L)).thenReturn(session);
        when(sessionMapper.endSession(99L)).thenReturn(1);

        assertThat(service.autoEndSession(99L)).isTrue();

        verify(interactionService).closeActiveInteraction(99L);
        verify(handRaiseService).clear(99L);
        verify(presenceService).clearSession(99L);
        verify(notificationService).notifySessionSummary(session);
        verify(messagingTemplate).convertAndSend(
                "/topic/session/99/teacher-status",
                (Object) Map.of(
                        "online", false,
                        "sessionEnded", true,
                        "reason", "TEACHER_OFFLINE_TIMEOUT"));
    }
}
