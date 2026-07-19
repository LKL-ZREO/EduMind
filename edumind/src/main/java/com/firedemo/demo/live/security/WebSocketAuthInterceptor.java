package com.firedemo.demo.live.security;

import com.firedemo.demo.live.service.StudentPresenceService;
import com.firedemo.demo.utils.JwtUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.event.EventListener;
import org.springframework.lang.NonNull;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Component
public class WebSocketAuthInterceptor implements ChannelInterceptor {

    private final JwtUtil jwtUtil;
    private final StudentPresenceService presenceService;
    private final SimpMessagingTemplate messagingTemplate;
    // WebSocket sessionId → (liveSessionId, studentId, studentName) — 学生连接追踪
    private final Map<String, SessionInfo> sessionMap = new ConcurrentHashMap<>();
    // liveSessionId → 该课堂教师 WebSocket 连接集合（一个教师可能多 tab 打开）
    private final Map<Long, Set<String>> teacherSessions = new ConcurrentHashMap<>();

    public WebSocketAuthInterceptor(JwtUtil jwtUtil, StudentPresenceService presenceService,
                                     @Lazy SimpMessagingTemplate messagingTemplate) {
        this.jwtUtil = jwtUtil;
        this.presenceService = presenceService;
        this.messagingTemplate = messagingTemplate;
    }

    @Override
    public Message<?> preSend(@NonNull Message<?> message, @NonNull MessageChannel channel) {
        StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
        if (accessor == null) return message;
        StompCommand command = accessor.getCommand();
        if (command == null) return message;

        switch (command) {
            case CONNECT -> handleConnect(accessor);
            case SEND -> handleSend(accessor);
            case SUBSCRIBE -> handleSubscribe(accessor);
            default -> {}
        }
        return message;
    }

    private void handleConnect(StompHeaderAccessor accessor) {
        List<String> authHeaders = accessor.getNativeHeader("Authorization");
        if (authHeaders == null || authHeaders.isEmpty())
            throw new IllegalArgumentException("缺少登录凭证");
        String token = authHeaders.get(0);
        if (token.startsWith("Bearer ")) token = token.substring(7);
        if (!jwtUtil.validateToken(token))
            throw new IllegalArgumentException("登录已过期");

        String username = jwtUtil.getUsernameFromToken(token);
        String role = jwtUtil.getRoleFromToken(token);
        Long userId = jwtUtil.getUserIdFromToken(token);
        // 用 userId 做 principal，保证 convertAndSendToUser 能正确路由到该连接
        accessor.setUser(() -> String.valueOf(userId));
        accessor.getSessionAttributes().put("username", username);
        accessor.getSessionAttributes().put("role", role);
        accessor.getSessionAttributes().put("userId", userId);
        accessor.getSessionAttributes().put("token", token);

        // 学生连接时通知教师
        if ("STUDENT".equals(role)) {
            Long sessionId = jwtUtil.getSessionIdFromToken(token);
            if (sessionId != null) {
                String wsSessionId = accessor.getSessionId();
                sessionMap.put(wsSessionId, new SessionInfo(sessionId, username, username));
                presenceService.studentJoined(sessionId, jwtUtil.getUserIdFromToken(token).toString(), username);
            }
        }
        // 教师连接时追踪
        if ("TEACHER".equals(role)) {
            List<String> sidHeaders = accessor.getNativeHeader("X-Session-Id");
            if (sidHeaders != null && !sidHeaders.isEmpty()) {
                Long sid = Long.valueOf(sidHeaders.get(0));
                teacherSessions.computeIfAbsent(sid, k -> ConcurrentHashMap.newKeySet())
                        .add(accessor.getSessionId());
                messagingTemplate.convertAndSend("/topic/session/" + sid + "/teacher-status",
                        (Object) Map.of("online", true));
                log.info("教师连接课堂: sessionId={}, user={}", sid, username);
            }
        }
        log.info("WS CONNECT: user={}, role={}", username, role);
    }

    @EventListener
    public void onDisconnect(SessionDisconnectEvent event) {
        String wsSessionId = event.getSessionId();
        // 学生断线 → 30 秒宽限期（避免切 Wi-Fi 闪烁）
        SessionInfo info = sessionMap.remove(wsSessionId);
        if (info != null) {
            presenceService.studentDisconnected(info.sessionId, info.studentId, info.studentName);
        }
        // 教师断线：从追踪中移除，如果该课堂无教师连接则广播 offline
        for (var entry : teacherSessions.entrySet()) {
            Set<String> conns = entry.getValue();
            if (conns.remove(wsSessionId)) {
                if (conns.isEmpty()) {
                    teacherSessions.remove(entry.getKey());
                    messagingTemplate.convertAndSend(
                            "/topic/session/" + entry.getKey() + "/teacher-status",
                            (Object) Map.of("online", false));
                    log.info("教师全部断线: sessionId={}", entry.getKey());
                }
                break;
            }
        }
    }

    record SessionInfo(Long sessionId, String studentId, String studentName) {}

    private void handleSend(StompHeaderAccessor accessor) {
        String dest = accessor.getDestination();
        String role = (String) accessor.getSessionAttributes().get("role");
        if (dest == null || role == null) return;
        if ("STUDENT".equals(role) &&
                (dest.contains("/interaction/create") || dest.contains("/interaction/close")
                 || dest.contains("/qa/answer") || dest.contains("/hand/call") || dest.contains("/hand/dismiss")))
            throw new IllegalArgumentException("学生无权执行此操作");
    }

    private void handleSubscribe(StompHeaderAccessor accessor) {
        String dest = accessor.getDestination();
        String role = (String) accessor.getSessionAttributes().get("role");
        if (dest == null || role == null) return;
        if ("STUDENT".equals(role) &&
                (dest.contains("/stats") || dest.contains("/qa") || dest.contains("/students")))
            throw new IllegalArgumentException("学生无权订阅此频道");
    }
}
