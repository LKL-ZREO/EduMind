package com.firedemo.demo.Service;

import com.firedemo.demo.Entity.ChatSession;
import com.firedemo.demo.mapper.ChatSessionMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ChatSessionService {

    private static final int MAX_TITLE_LENGTH = 60;
    private final ChatSessionMapper mapper;

    public List<ChatSession> list(Long userId) {
        return mapper.selectByUserId(userId);
    }

    public ChatSession findOwned(Long userId, String sessionId) {
        if (userId == null || sessionId == null || sessionId.isBlank()) return null;
        return mapper.selectOwned(userId, sessionId);
    }

    @Transactional(rollbackFor = Exception.class)
    public ChatSession create(Long userId, String title, Long classId, Long courseId,
                              Set<Long> selectedKbIds, String mode) {
        ChatSession session = new ChatSession();
        session.setSessionId(UUID.randomUUID().toString());
        session.setUserId(userId);
        session.setTitle(normalizeTitle(title));
        session.setClassId(classId);
        session.setCourseId(courseId);
        session.setSelectedKbIds(encodeKbIds(selectedKbIds));
        session.setMode(normalizeMode(mode));
        session.setPinned(false);
        mapper.insertIfAbsent(session);
        return mapper.selectOwned(userId, session.getSessionId());
    }

    @Transactional(rollbackFor = Exception.class)
    public ChatSession ensure(Long userId, String sessionId, String firstMessage,
                              Long classId, Long courseId, Set<Long> selectedKbIds,
                              String mode) {
        ChatSession current = findOwned(userId, sessionId);
        if (current == null) {
            current = new ChatSession();
            current.setSessionId(sessionId);
            current.setUserId(userId);
            current.setTitle(autoTitle(firstMessage));
            current.setClassId(classId);
            current.setCourseId(courseId);
            current.setSelectedKbIds(encodeKbIds(selectedKbIds));
            current.setMode(normalizeMode(mode));
            current.setPinned(false);
            mapper.insertIfAbsent(current);
            current = mapper.selectOwned(userId, sessionId);
            if (current == null) {
                throw new org.springframework.web.server.ResponseStatusException(
                        org.springframework.http.HttpStatus.FORBIDDEN, "无权使用该会话");
            }
        } else {
            current.setClassId(classId);
            current.setCourseId(courseId);
            current.setSelectedKbIds(encodeKbIds(selectedKbIds));
            current.setMode(normalizeMode(mode));
            mapper.updateOwned(current);
            mapper.touchAndAutoTitle(userId, sessionId, autoTitle(firstMessage));
        }
        return mapper.selectOwned(userId, sessionId);
    }

    @Transactional(rollbackFor = Exception.class)
    public ChatSession update(Long userId, String sessionId, String title,
                              Long classId, Long courseId, Set<Long> selectedKbIds,
                              String mode, Boolean pinned) {
        ChatSession session = findOwned(userId, sessionId);
        if (session == null) return null;
        if (title != null) session.setTitle(normalizeTitle(title));
        session.setClassId(classId);
        session.setCourseId(courseId);
        session.setSelectedKbIds(encodeKbIds(selectedKbIds));
        if (mode != null) session.setMode(normalizeMode(mode));
        if (pinned != null) session.setPinned(pinned);
        mapper.updateOwned(session);
        return mapper.selectOwned(userId, sessionId);
    }

    @Transactional(rollbackFor = Exception.class)
    public boolean delete(Long userId, String sessionId) {
        return mapper.deleteOwned(userId, sessionId) > 0;
    }

    @Transactional(rollbackFor = Exception.class)
    public void deleteByUserId(Long userId) {
        mapper.deleteByUserId(userId);
    }

    public Set<Long> decodeKbIds(ChatSession session) {
        if (session == null || session.getSelectedKbIds() == null
                || session.getSelectedKbIds().isBlank()) return Set.of();
        try {
            return java.util.Arrays.stream(session.getSelectedKbIds().split(","))
                    .map(String::trim)
                    .filter(value -> !value.isEmpty())
                    .map(Long::valueOf)
                    .collect(Collectors.toCollection(LinkedHashSet::new));
        } catch (NumberFormatException ignored) {
            return Set.of();
        }
    }

    private String encodeKbIds(Set<Long> ids) {
        if (ids == null || ids.isEmpty()) return "";
        return ids.stream().sorted().map(String::valueOf).collect(Collectors.joining(","));
    }

    private String normalizeTitle(String value) {
        if (value == null || value.isBlank()) return "新对话";
        String title = value.replace('\n', ' ').replace('\r', ' ').trim();
        return title.length() <= MAX_TITLE_LENGTH ? title : title.substring(0, MAX_TITLE_LENGTH);
    }

    private String autoTitle(String firstMessage) {
        return normalizeTitle(firstMessage);
    }

    private String normalizeMode(String value) {
        if ("lesson_plan".equals(value)) return value;
        if ("learning_analysis".equals(value)) return value;
        if ("grading".equals(value)) return value;
        return "auto";
    }
}
