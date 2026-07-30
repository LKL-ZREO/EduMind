package com.firedemo.demo.Service;

import com.firedemo.demo.Entity.TeacherKnowledge;
import com.firedemo.demo.mapper.TeacherKnowledgeMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 教师知识点是受控词表。LLM 只能返回词表中的精确名称，否则统一落入“其他”。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class KnowledgePointVocabularyService {

    public static final String OTHER = "其他";

    private final TeacherKnowledgeMapper teacherKnowledgeMapper;

    public Map<String, String> loadCanonicalNames(Long classId) {
        Map<String, String> names = new LinkedHashMap<>();
        if (classId != null) {
            List<TeacherKnowledge> configured = teacherKnowledgeMapper.selectByClassId(classId);
            for (TeacherKnowledge knowledge : configured) {
                String canonical = trimToNull(knowledge.getName());
                if (canonical != null) names.put(canonical, canonical);
            }
        }
        names.put(OTHER, OTHER);
        return Map.copyOf(names);
    }

    public String normalize(String candidate, Map<String, String> canonicalNames) {
        String normalized = trimToNull(candidate);
        if (normalized == null) return OTHER;
        String canonical = canonicalNames.get(normalized);
        if (canonical != null) return canonical;
        log.warn("Rejected non-canonical knowledge point returned by AI: {}", normalized);
        return OTHER;
    }

    private String trimToNull(String value) {
        if (value == null) return null;
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
