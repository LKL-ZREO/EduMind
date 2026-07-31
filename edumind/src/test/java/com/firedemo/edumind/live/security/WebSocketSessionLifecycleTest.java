package com.firedemo.edumind.live.security;

import org.junit.jupiter.api.Test;
import org.springframework.session.MapSession;
import org.springframework.session.events.SessionDeletedEvent;
import org.springframework.session.events.SessionExpiredEvent;
import org.springframework.session.web.socket.events.SessionConnectEvent;
import org.springframework.session.web.socket.handler.WebSocketRegistryListener;
import org.springframework.session.web.socket.server.SessionRepositoryMessageInterceptor;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.WebSocketSession;

import java.security.Principal;
import java.util.HashMap;
import java.util.Map;

import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class WebSocketSessionLifecycleTest {

    @Test
    void deletingHttpSessionClosesEveryAssociatedTeacherWebSocket() throws Exception {
        WebSocketRegistryListener listener = new WebSocketRegistryListener();
        WebSocketSession firstTab = teacherSocket("ws-1", "http-session-1");
        WebSocketSession secondTab = teacherSocket("ws-2", "http-session-1");
        listener.onApplicationEvent(new SessionConnectEvent(this, firstTab));
        listener.onApplicationEvent(new SessionConnectEvent(this, secondTab));

        listener.onApplicationEvent(new SessionDeletedEvent(
                this,
                new MapSession("http-session-1")));

        verify(firstTab).close(argThat(this::isSessionExpiredStatus));
        verify(secondTab).close(argThat(this::isSessionExpiredStatus));
    }

    @Test
    void expiringHttpSessionClosesAssociatedTeacherWebSocket() throws Exception {
        WebSocketRegistryListener listener = new WebSocketRegistryListener();
        WebSocketSession socket = teacherSocket("ws-1", "http-session-1");
        listener.onApplicationEvent(new SessionConnectEvent(this, socket));

        listener.onApplicationEvent(new SessionExpiredEvent(
                this,
                new MapSession("http-session-1")));

        verify(socket).close(argThat(this::isSessionExpiredStatus));
    }

    private WebSocketSession teacherSocket(String webSocketId, String httpSessionId) {
        WebSocketSession session = mock(WebSocketSession.class);
        Map<String, Object> attributes = new HashMap<>();
        SessionRepositoryMessageInterceptor.setSessionId(attributes, httpSessionId);
        when(session.getId()).thenReturn(webSocketId);
        when(session.getAttributes()).thenReturn(attributes);
        when(session.getPrincipal()).thenReturn((Principal) () -> "teacher");
        return session;
    }

    private boolean isSessionExpiredStatus(CloseStatus status) {
        return status != null
                && status.getCode() == CloseStatus.POLICY_VIOLATION.getCode()
                && status.getReason() != null
                && status.getReason().contains("HTTP Session");
    }
}
