package com.firedemo.demo.Controller;

import com.firedemo.demo.Entity.ChatHistory;
import com.firedemo.demo.Entity.ChatSession;
import com.firedemo.demo.Service.ChatContextService;
import com.firedemo.demo.Service.ChatHistoryService;
import com.firedemo.demo.Service.ChatSessionService;
import com.firedemo.demo.Service.OpenClawService;
import com.firedemo.demo.mapper.ClassInfoMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;

@RestController
@RequestMapping("/api/chat/sessions")
@RequiredArgsConstructor
public class ChatSessionController {

    private final ChatSessionService sessionService;
    private final ChatHistoryService historyService;
    private final ChatContextService contextService;
    private final ClassInfoMapper classInfoMapper;
    private final OpenClawService agentService;

    @GetMapping
    public ResponseEntity<List<SessionView>> list() {
        Long userId = requireUserId();
        return ResponseEntity.ok(sessionService.list(userId).stream().map(this::toView).toList());
    }

    @PostMapping
    public ResponseEntity<SessionView> create(@RequestBody(required = false) SessionMutation body) {
        Long userId = requireUserId();
        SessionMutation request = body != null ? body : new SessionMutation(null, null, null, null, null);
        ChatContextService.Scope scope = contextService.resolve(userId, request.classId(), request.kbIds());
        ChatSession created = sessionService.create(
                userId, request.title(), request.classId(), scope.courseId(),
                scope.selectedKbIds(), request.mode());
        return ResponseEntity.ok(toView(created));
    }

    @GetMapping("/{sessionId}/messages")
    public ResponseEntity<List<ChatHistory>> messages(@PathVariable String sessionId) {
        Long userId = requireUserId();
        if (sessionService.findOwned(userId, sessionId) == null) return ResponseEntity.notFound().build();
        return ResponseEntity.ok(historyService.getHistory(userId, sessionId, 200));
    }

    @PatchMapping("/{sessionId}")
    public ResponseEntity<SessionView> update(@PathVariable String sessionId,
                                               @RequestBody SessionMutation request) {
        Long userId = requireUserId();
        ChatSession existing = sessionService.findOwned(userId, sessionId);
        if (existing == null) return ResponseEntity.notFound().build();

        Set<Long> kbIds = request.kbIds() != null
                ? request.kbIds() : sessionService.decodeKbIds(existing);
        Long classId = request.classId();
        ChatContextService.Scope scope = contextService.resolve(userId, classId, kbIds);
        ChatSession updated = sessionService.update(
                userId, sessionId, request.title(), classId, scope.courseId(),
                scope.selectedKbIds(), request.mode(), request.pinned());
        return ResponseEntity.ok(toView(updated));
    }

    @DeleteMapping("/{sessionId}")
    public ResponseEntity<Map<String, String>> delete(@PathVariable String sessionId) {
        Long userId = requireUserId();
        if (sessionService.findOwned(userId, sessionId) == null) return ResponseEntity.notFound().build();
        historyService.deleteSession(userId, sessionId);
        sessionService.delete(userId, sessionId);
        agentService.clearMemory(userId, sessionId);
        return ResponseEntity.ok(Map.of("message", "会话已删除"));
    }

    private SessionView toView(ChatSession session) {
        String className = null;
        if (session.getClassId() != null) {
            var classInfo = classInfoMapper.selectById(session.getClassId());
            if (classInfo != null) className = classInfo.getName();
        }
        return new SessionView(
                session.getSessionId(), session.getTitle(), session.getClassId(), className,
                session.getCourseId(), sessionService.decodeKbIds(session), session.getMode(),
                Boolean.TRUE.equals(session.getPinned()), session.getCreatedAt(), session.getUpdatedAt());
    }

    private Long requireUserId() {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || !(auth.getDetails() instanceof Long userId)) {
            throw new org.springframework.web.server.ResponseStatusException(
                    org.springframework.http.HttpStatus.UNAUTHORIZED, "未登录");
        }
        return userId;
    }

    public record SessionMutation(
            String title,
            Long classId,
            Set<Long> kbIds,
            String mode,
            Boolean pinned
    ) {
    }

    public record SessionView(
            String sessionId,
            String title,
            Long classId,
            String className,
            Long courseId,
            Set<Long> kbIds,
            String mode,
            boolean pinned,
            LocalDateTime createdAt,
            LocalDateTime updatedAt
    ) {
    }
}
