package com.firedemo.edumind.live.security;

import com.firedemo.edumind.auth.authorization.OwnershipGuard;
import com.firedemo.edumind.live.service.StudentPresenceService;
import com.firedemo.edumind.live.ClassroomSessionMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;

import java.util.HashMap;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class WebSocketAuthInterceptorTest {

    private LiveSessionTokenService tokenService;
    private StudentPresenceService presenceService;
    private SimpMessagingTemplate messagingTemplate;
    private OwnershipGuard ownershipGuard;
    private ClassroomSessionMapper sessionMapper;
    private WebSocketAuthInterceptor interceptor;

    @BeforeEach
    void setUp() {
        tokenService = mock(LiveSessionTokenService.class);
        presenceService = mock(StudentPresenceService.class);
        messagingTemplate = mock(SimpMessagingTemplate.class);
        ownershipGuard = mock(OwnershipGuard.class);
        sessionMapper = mock(ClassroomSessionMapper.class);
        interceptor = new WebSocketAuthInterceptor(
                tokenService, presenceService, messagingTemplate, ownershipGuard, sessionMapper);
    }

    @Test
    void authenticatesTeacherFromHttpSessionHandshakePrincipal() {
        StompHeaderAccessor accessor = connectAccessor();
        var teacher = UsernamePasswordAuthenticationToken.authenticated(
                "teacher",
                null,
                List.of(new SimpleGrantedAuthority("ROLE_TEACHER")));
        teacher.setDetails(10L);
        accessor.setUser(teacher);
        accessor.setNativeHeader("X-Session-Id", "99");
        when(ownershipGuard.isSessionOwner(10L, 99L)).thenReturn(true);

        Message<?> result = interceptor.preSend(message(accessor), mock(MessageChannel.class));
        StompHeaderAccessor authenticated = StompHeaderAccessor.wrap(result);

        assertThat(authenticated.getUser().getName()).isEqualTo("10");
        assertThat(authenticated.getSessionAttributes())
                .containsEntry("userId", 10L)
                .containsEntry("role", "TEACHER");
        verify(messagingTemplate).convertAndSend(
                "/topic/session/99/teacher-status", (Object) java.util.Map.of("online", true));
        verify(sessionMapper).markTeacherOnline(99L);
    }

    @Test
    void marksTeacherOfflineWhenLastClassroomSocketDisconnects() {
        StompHeaderAccessor accessor = connectAccessor();
        var teacher = UsernamePasswordAuthenticationToken.authenticated(
                "teacher",
                null,
                List.of(new SimpleGrantedAuthority("ROLE_TEACHER")));
        teacher.setDetails(10L);
        accessor.setUser(teacher);
        accessor.setNativeHeader("X-Session-Id", "99");
        when(ownershipGuard.isSessionOwner(10L, 99L)).thenReturn(true);
        Message<byte[]> connectMessage = message(accessor);
        interceptor.preSend(connectMessage, mock(MessageChannel.class));

        interceptor.onDisconnect(new SessionDisconnectEvent(
                this, connectMessage, "ws-1", CloseStatus.NORMAL));

        verify(sessionMapper).markTeacherOffline(99L);
        verify(messagingTemplate).convertAndSend(
                "/topic/session/99/teacher-status", (Object) java.util.Map.of("online", false));
    }

    @Test
    void authenticatesStudentFromClassroomScopedToken() {
        ClassroomStudentPrincipal student =
                new ClassroomStudentPrincipal("S001", "student", 99L);
        when(tokenService.parse("student-token")).thenReturn(Optional.of(student));
        when(ownershipGuard.isLiveSessionActive(99L)).thenReturn(true);
        StompHeaderAccessor accessor = connectAccessor();
        accessor.setNativeHeader("Authorization", "Bearer student-token");

        Message<?> result = interceptor.preSend(message(accessor), mock(MessageChannel.class));
        StompHeaderAccessor authenticated = StompHeaderAccessor.wrap(result);

        assertThat(authenticated.getUser().getName()).isEqualTo("S001");
        assertThat(authenticated.getSessionAttributes())
                .containsEntry("role", "STUDENT")
                .containsEntry("studentId", "S001")
                .containsEntry("liveSessionId", 99L);
        verify(presenceService).studentJoined(99L, "S001", "student");
    }

    @Test
    void rejectsTeacherConnectingToAnotherTeachersClassroom() {
        StompHeaderAccessor accessor = connectAccessor();
        var teacher = UsernamePasswordAuthenticationToken.authenticated(
                "teacher",
                null,
                List.of(new SimpleGrantedAuthority("ROLE_TEACHER")));
        teacher.setDetails(10L);
        accessor.setUser(teacher);
        accessor.setNativeHeader("X-Session-Id", "99");
        when(ownershipGuard.isSessionOwner(10L, 99L)).thenReturn(false);

        assertThatThrownBy(() -> interceptor.preSend(message(accessor), mock(MessageChannel.class)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("无权连接该课堂");
    }

    @Test
    void rejectsStudentSendOutsideTokenClassroom() {
        StompHeaderAccessor accessor = commandAccessor(
                StompCommand.SEND,
                "/app/session/100/interaction/7/respond",
                "STUDENT",
                99L);

        assertThatThrownBy(() -> interceptor.preSend(message(accessor), mock(MessageChannel.class)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("无权访问其他课堂");
    }

    @Test
    void allowsStudentResponseInsideTokenClassroom() {
        StompHeaderAccessor accessor = commandAccessor(
                StompCommand.SEND,
                "/app/session/99/interaction/7/respond",
                "STUDENT",
                99L);

        assertThat(interceptor.preSend(message(accessor), mock(MessageChannel.class))).isNotNull();
    }

    @Test
    void rejectsTeacherImpersonatingStudentResponse() {
        StompHeaderAccessor accessor = commandAccessor(
                StompCommand.SEND,
                "/app/session/99/interaction/7/respond",
                "TEACHER",
                99L);

        assertThatThrownBy(() -> interceptor.preSend(message(accessor), mock(MessageChannel.class)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("当前身份无权发送到该目的地址");
    }

    @Test
    void rejectsStudentSubscriptionToAnotherClassroom() {
        StompHeaderAccessor accessor = commandAccessor(
                StompCommand.SUBSCRIBE,
                "/topic/session/100/interaction",
                "STUDENT",
                99L);

        assertThatThrownBy(() -> interceptor.preSend(message(accessor), mock(MessageChannel.class)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("无权访问其他课堂");
    }

    @Test
    void rejectsStudentSubscriptionToTeacherStatistics() {
        StompHeaderAccessor accessor = commandAccessor(
                StompCommand.SUBSCRIBE,
                "/topic/session/99/stats",
                "STUDENT",
                99L);

        assertThatThrownBy(() -> interceptor.preSend(message(accessor), mock(MessageChannel.class)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("学生无权订阅此频道");
    }

    @Test
    void rejectsConnectWithoutTeacherSessionOrClassroomToken() {
        StompHeaderAccessor accessor = connectAccessor();

        assertThatThrownBy(() -> interceptor.preSend(message(accessor), mock(MessageChannel.class)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("缺少教师登录会话");
    }

    private StompHeaderAccessor connectAccessor() {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.CONNECT);
        accessor.setSessionId("ws-1");
        accessor.setSessionAttributes(new HashMap<>());
        return accessor;
    }

    private StompHeaderAccessor commandAccessor(StompCommand command,
                                                String destination,
                                                String role,
                                                Long liveSessionId) {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(command);
        accessor.setSessionId("ws-1");
        accessor.setDestination(destination);
        HashMap<String, Object> attributes = new HashMap<>();
        attributes.put("role", role);
        attributes.put("liveSessionId", liveSessionId);
        accessor.setSessionAttributes(attributes);
        return accessor;
    }

    private Message<byte[]> message(StompHeaderAccessor accessor) {
        accessor.setLeaveMutable(true);
        return MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());
    }
}
