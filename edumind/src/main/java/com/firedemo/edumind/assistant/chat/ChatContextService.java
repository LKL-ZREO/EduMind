package com.firedemo.edumind.assistant.chat;

import com.firedemo.edumind.classroom.ClassInfo;
import com.firedemo.edumind.classroom.ClassInfoMapper;
import com.firedemo.edumind.knowledge.SharedKbMemberMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.LinkedHashSet;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class ChatContextService {

    private final ClassInfoMapper classInfoMapper;
    private final SharedKbMemberMapper sharedKbMemberMapper;

    public Scope resolve(Long userId, Long classId, Set<Long> requestedKbIds) {
        if (userId == null) {
            return new Scope(null, null, Set.of());
        }

        ClassInfo selectedClass = null;
        if (classId != null) {
            selectedClass = classInfoMapper.selectById(classId);
            if (selectedClass == null || !userId.equals(selectedClass.getTeacherId())) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN, "无权访问所选班级");
            }
        }

        Set<Long> accessible = sharedKbMemberMapper.selectKbIdsByUserId(userId);
        if (accessible == null) accessible = Set.of();
        Set<Long> selected;
        if (requestedKbIds == null) {
            selected = new LinkedHashSet<>(accessible);
        } else {
            selected = new LinkedHashSet<>(requestedKbIds);
            if (!accessible.containsAll(selected)) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN, "包含无权访问的知识库");
            }
        }

        return new Scope(
                selectedClass,
                selectedClass != null ? selectedClass.getCourseId() : null,
                Set.copyOf(selected));
    }

    public record Scope(ClassInfo selectedClass, Long courseId, Set<Long> selectedKbIds) {
    }
}
