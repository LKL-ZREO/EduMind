package com.firedemo.edumind.live.security;

import com.firedemo.edumind.auth.authorization.OwnershipGuard;
import com.firedemo.edumind.live.service.StudentPresenceService;
import com.firedemo.edumind.live.ClassroomSessionMapper;
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
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;

import java.util.List;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

@Slf4j
@Component
public class WebSocketAuthInterceptor implements ChannelInterceptor {

    private static final Pattern APP_SESSION_DESTINATION =
            Pattern.compile("^/app/session/(\\d+)(?:/.*)?$");
    private static final Pattern TOPIC_SESSION_DESTINATION =
            Pattern.compile("^/topic/session/(\\d+)(?:/.*)?$");
    private static final List<Pattern> STUDENT_SEND_DESTINATIONS = List.of(
            Pattern.compile("^/app/session/\\d+/interaction/\\d+/respond$"),
            Pattern.compile("^/app/session/\\d+/qa/ask$"),
            Pattern.compile("^/app/session/\\d+/reaction$"),
            Pattern.compile("^/app/session/\\d+/hand/(?:raise|lower)$"));
    private static final List<Pattern> TEACHER_SEND_DESTINATIONS = List.of(
            Pattern.compile("^/app/session/\\d+/interaction/create$"),
            Pattern.compile("^/app/session/\\d+/interaction/\\d+/close$"),
            Pattern.compile("^/app/session/\\d+/qa/\\d+/answer$"),
            Pattern.compile("^/app/session/\\d+/hand/(?:call|dismiss)$"));
    private static final Set<String> STUDENT_TOPIC_SUFFIXES = Set.of(
            "/interaction", "/reactions", "/hand-queue", "/teacher-status");

    private final LiveSessionTokenService liveSessionTokenService;
    private final StudentPresenceService presenceService;
    private final SimpMessagingTemplate messagingTemplate;
    private final OwnershipGuard ownershipGuard;
    private final ClassroomSessionMapper sessionMapper;
    // WebSocket sessionId → (liveSessionId, studentId, studentName) — 学生连接追踪
    private final Map<String, SessionInfo> sessionMap = new ConcurrentHashMap<>();
    // liveSessionId → 该课堂教师 WebSocket 连接集合（一个教师可能多 tab 打开）
    private final Map<Long, Set<String>> teacherSessions = new ConcurrentHashMap<>();

    public WebSocketAuthInterceptor(LiveSessionTokenService liveSessionTokenService,
                                     StudentPresenceService presenceService,
                                     @Lazy SimpMessagingTemplate messagingTemplate,
                                     OwnershipGuard ownershipGuard,
                                     ClassroomSessionMapper sessionMapper) {
        this.liveSessionTokenService = liveSessionTokenService;
        this.presenceService = presenceService;
        this.messagingTemplate = messagingTemplate;
        this.ownershipGuard = ownershipGuard;
        this.sessionMapper = sessionMapper;
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
        if (authHeaders != null && !authHeaders.isEmpty()) {
            authenticateStudent(accessor, authHeaders.get(0));
            return;
        }
        authenticateTeacher(accessor);
    }

    private void authenticateStudent(StompHeaderAccessor accessor, String header) {
        String token = header.startsWith("Bearer ") ? header.substring(7) : header;
        ClassroomStudentPrincipal student = liveSessionTokenService.parse(token)
                .orElseThrow(() -> new IllegalArgumentException("课堂凭证无效或已过期"));
        if (!ownershipGuard.isLiveSessionActive(student.liveSessionId())) {
            throw new IllegalArgumentException("课堂不存在或已结束");
        }
        Map<String, Object> attributes = sessionAttributes(accessor);
        accessor.setUser(student);
        attributes.put("username", student.studentName());
        attributes.put("role", "STUDENT");
        attributes.put("studentId", student.studentId());
        attributes.put("userId", student.studentId());
        attributes.put("liveSessionId", student.liveSessionId());

        sessionMap.put(accessor.getSessionId(), new SessionInfo(
                student.liveSessionId(), student.studentId(), student.studentName()));
        presenceService.studentJoined(
                student.liveSessionId(), student.studentId(), student.studentName());
        log.info("WS CONNECT: user={}, role=STUDENT", student.studentId());
    }

    private void authenticateTeacher(StompHeaderAccessor accessor) {
        if (!(accessor.getUser() instanceof Authentication authentication)
                || !authentication.isAuthenticated()
                || authentication.getAuthorities().stream()
                .noneMatch(authority -> "ROLE_TEACHER".equals(authority.getAuthority()))
                || !(authentication.getDetails() instanceof Long userId)) {
            throw new IllegalArgumentException("缺少教师登录会话");
        }

        Map<String, Object> attributes = sessionAttributes(accessor);
        String username = authentication.getName();
        accessor.setUser(() -> String.valueOf(userId));
        attributes.put("username", username);
        attributes.put("role", "TEACHER");
        attributes.put("userId", userId);

        List<String> sidHeaders = accessor.getNativeHeader("X-Session-Id");
        if (sidHeaders != null && !sidHeaders.isEmpty()) {
            Long sid = parseSessionId(sidHeaders.get(0));
            if (!ownershipGuard.isSessionOwner(userId, sid)) {
                throw new IllegalArgumentException("无权连接该课堂");
            }
            attributes.put("liveSessionId", sid);
            teacherSessions.computeIfAbsent(sid, key -> ConcurrentHashMap.newKeySet())
                    .add(accessor.getSessionId());
            sessionMapper.markTeacherOnline(sid);
            messagingTemplate.convertAndSend("/topic/session/" + sid + "/teacher-status",
                    (Object) Map.of("online", true));
            log.info("教师连接课堂: sessionId={}, user={}", sid, username);
        }
        log.info("WS CONNECT: user={}, role=TEACHER", username);
    }

    private Map<String, Object> sessionAttributes(StompHeaderAccessor accessor) {
        Map<String, Object> attributes = accessor.getSessionAttributes();
        if (attributes == null) {
            attributes = new HashMap<>();
            accessor.setSessionAttributes(attributes);
        }
        return attributes;
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
                    sessionMapper.markTeacherOffline(entry.getKey());
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
        if (dest == null) throw new IllegalArgumentException("缺少消息目的地址");
        String role = requireRole(accessor);
        requireBoundSession(accessor, dest, APP_SESSION_DESTINATION);

        List<Pattern> allowed = "STUDENT".equals(role)
                ? STUDENT_SEND_DESTINATIONS
                : TEACHER_SEND_DESTINATIONS;
        if (allowed.stream().noneMatch(pattern -> pattern.matcher(dest).matches())) {
            throw new IllegalArgumentException("当前身份无权发送到该目的地址");
        }
    }

    private void handleSubscribe(StompHeaderAccessor accessor) {
        String dest = accessor.getDestination();
        if (dest == null) throw new IllegalArgumentException("缺少订阅目的地址");
        String role = requireRole(accessor);
        Long sessionId = requireBoundSession(accessor, dest, TOPIC_SESSION_DESTINATION);
        if ("STUDENT".equals(role)) {
            String prefix = "/topic/session/" + sessionId;
            String suffix = dest.substring(prefix.length());
            if (!STUDENT_TOPIC_SUFFIXES.contains(suffix)) {
                throw new IllegalArgumentException("学生无权订阅此频道");
            }
        }
    }

    private String requireRole(StompHeaderAccessor accessor) {
        Map<String, Object> attributes = accessor.getSessionAttributes();
        Object role = attributes != null ? attributes.get("role") : null;
        if (!"TEACHER".equals(role) && !"STUDENT".equals(role)) {
            throw new IllegalArgumentException("WebSocket 会话尚未认证");
        }
        return role.toString();
    }

    private Long requireBoundSession(StompHeaderAccessor accessor,
                                     String destination,
                                     Pattern destinationPattern) {
        var matcher = destinationPattern.matcher(destination);
        if (!matcher.matches()) {
            throw new IllegalArgumentException("不允许访问该目的地址");
        }
        Long targetSessionId = parseSessionId(matcher.group(1));
        Map<String, Object> attributes = accessor.getSessionAttributes();
        Object boundValue = attributes != null ? attributes.get("liveSessionId") : null;
        Long boundSessionId = toLong(boundValue);
        if (!targetSessionId.equals(boundSessionId)) {
            throw new IllegalArgumentException("无权访问其他课堂");
        }
        return targetSessionId;
    }

    private Long parseSessionId(String value) {
        try {
            return Long.valueOf(value);
        } catch (RuntimeException e) {
            throw new IllegalArgumentException("课堂 ID 无效");
        }
    }

    private Long toLong(Object value) {
        if (value instanceof Number number) return number.longValue();
        if (value instanceof String text) return parseSessionId(text);
        return null;
    }
}
