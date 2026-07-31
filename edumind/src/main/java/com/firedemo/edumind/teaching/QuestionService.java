package com.firedemo.edumind.teaching;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.firedemo.edumind.live.InteractionCreateDTO;
import com.firedemo.edumind.shared.exception.BusinessException;
import com.firedemo.edumind.shared.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class QuestionService {
    private static final Set<String> TYPES = Set.of("CHOICE", "OPEN", "EXERCISE", "HOMEWORK");
    private static final Set<String> DIFFICULTIES = Set.of("easy", "medium", "hard");

    private final QuestionBankItemMapper mapper;
    private final ObjectMapper objectMapper;
    public List<QuestionDTO> search(Long teacherId, String keyword, String sourceDocId, String type) {
        return searchEntities(teacherId, keyword, sourceDocId, type).stream().map(this::toDTO).toList();
    }
    public List<QuestionBankItem> searchEntities(Long teacherId, String keyword, String sourceDocId, String type) {
        requireTeacher(teacherId);
        String normalizedType = normalizeNullable(type);
        if (normalizedType != null && !TYPES.contains(normalizedType)) {
            throw new BusinessException(ErrorCode.PARAM_ERROR.getCode(), "不支持的题型");
        }
        return mapper.searchByTeacher(teacherId, trimToNull(keyword), trimToNull(sourceDocId), normalizedType);
    }
    public QuestionDTO get(Long teacherId, Long id) {
        return toDTO(requireOwnedEntity(teacherId, id));
    }
    @Transactional
    public QuestionDTO create(Long teacherId, QuestionUpsertDTO request) {
        requireTeacher(teacherId);
        if (request == null || trimToNull(request.getTitle()) == null) {
            throw new BusinessException(ErrorCode.PARAM_ERROR.getCode(), "题目内容不能为空");
        }
        LocalDateTime now = LocalDateTime.now();
        QuestionBankItem item = new QuestionBankItem();
        item.setTeacherId(teacherId);
        item.setType(normalizeType(request.getType()));
        item.setTitle(request.getTitle().trim());
        item.setRequirement(trimToNull(request.getRequirement()));
        item.setOptions(writeOptions(request.getOptions()));
        item.setCorrectKey(trimToNull(request.getCorrectKey()));
        item.setExplanation(trimToNull(request.getExplanation()));
        item.setKnowledgePoint(trimToNull(request.getKnowledgePoint()));
        item.setDifficulty(normalizeDifficulty(request.getDifficulty()));
        item.setDefaultTimeLimit(request.getTimeLimit());
        item.setScore(request.getScore() != null ? request.getScore() : 0);
        item.setUploadRequired(request.getUploadRequired() != null ? request.getUploadRequired() : true);
        item.setSourceDocId(trimToNull(request.getSourceDocId()));
        item.setAiGenerated(Boolean.TRUE.equals(request.getAiGenerated()));
        item.setArchived(false);
        item.setCreatedAt(now);
        item.setUpdatedAt(now);
        validate(item);
        mapper.insertWithJsonb(item);
        return toDTO(item);
    }
    @Transactional
    public QuestionDTO update(Long teacherId, Long id, QuestionUpsertDTO request) {
        QuestionBankItem item = requireOwnedEntity(teacherId, id);
        if (request == null) return toDTO(item);

        if (request.getType() != null) item.setType(normalizeType(request.getType()));
        if (request.getTitle() != null) item.setTitle(request.getTitle().trim());
        if (request.getRequirement() != null) item.setRequirement(trimToNull(request.getRequirement()));
        if (request.getOptions() != null) item.setOptions(writeOptions(request.getOptions()));
        if (request.getCorrectKey() != null) item.setCorrectKey(trimToNull(request.getCorrectKey()));
        if (request.getExplanation() != null) item.setExplanation(trimToNull(request.getExplanation()));
        if (request.getKnowledgePoint() != null) item.setKnowledgePoint(trimToNull(request.getKnowledgePoint()));
        if (request.getDifficulty() != null) item.setDifficulty(normalizeDifficulty(request.getDifficulty()));
        if (request.getTimeLimit() != null) item.setDefaultTimeLimit(request.getTimeLimit());
        if (request.getScore() != null) item.setScore(request.getScore());
        if (request.getUploadRequired() != null) item.setUploadRequired(request.getUploadRequired());
        if (request.getSourceDocId() != null) item.setSourceDocId(trimToNull(request.getSourceDocId()));
        if (request.getAiGenerated() != null) item.setAiGenerated(request.getAiGenerated());
        item.setUpdatedAt(LocalDateTime.now());
        validate(item);
        mapper.updateOwnedWithJsonb(item);
        return toDTO(item);
    }
    @Transactional
    public void archive(Long teacherId, Long id) {
        requireTeacher(teacherId);
        if (mapper.archiveOwned(id, teacherId) == 0) {
            QuestionBankItem existing = mapper.selectById(id);
            if (existing != null && !teacherId.equals(existing.getTeacherId())) {
                throw new BusinessException(ErrorCode.FORBIDDEN.getCode(), "无权操作该题目");
            }
        }
    }
    public QuestionBankItem requireOwnedEntity(Long teacherId, Long id) {
        requireTeacher(teacherId);
        QuestionBankItem item = id != null ? mapper.selectById(id) : null;
        if (item == null || Boolean.TRUE.equals(item.getArchived())) {
            throw new BusinessException(ErrorCode.DATA_NOT_FOUND.getCode(), "题目不存在");
        }
        if (!teacherId.equals(item.getTeacherId())) {
            throw new BusinessException(ErrorCode.FORBIDDEN.getCode(), "无权操作该题目");
        }
        return item;
    }
    public QuestionBankItem findEntity(Long id) {
        return id != null ? mapper.selectById(id) : null;
    }
    public QuestionDTO findDTO(Long id) {
        QuestionBankItem item = findEntity(id);
        return item != null ? toDTO(item) : null;
    }
    @Transactional
    public QuestionBankItem saveHomeworkQuestion(Long teacherId, Long id, String title,
                                                 String requirement, Integer score,
                                                 Boolean uploadRequired) {
        requireTeacher(teacherId);
        QuestionBankItem item = id != null ? mapper.selectById(id) : null;
        if (item != null && !teacherId.equals(item.getTeacherId())) item = null;

        LocalDateTime now = LocalDateTime.now();
        if (item == null) {
            item = new QuestionBankItem();
            item.setTeacherId(teacherId);
            item.setType("HOMEWORK");
            item.setAiGenerated(false);
            item.setArchived(false);
            item.setCreatedAt(now);
        }
        item.setTitle(trimToNull(title) != null ? title.trim() : "未命名题目");
        item.setRequirement(trimToNull(requirement));
        item.setScore(score != null ? score : 0);
        item.setUploadRequired(uploadRequired != null ? uploadRequired : true);
        item.setUpdatedAt(now);
        validate(item);
        if (item.getId() == null) mapper.insertWithJsonb(item);
        else mapper.updateOwnedWithJsonb(item);
        return item;
    }
    @Transactional
    public QuestionBankItem createFromInteraction(Long teacherId, InteractionCreateDTO request) {
        QuestionUpsertDTO dto = new QuestionUpsertDTO();
        dto.setType(request.getType());
        dto.setTitle(request.getTitle());
        dto.setRequirement(request.getDescription());
        dto.setCorrectKey(request.getCorrectKey());
        dto.setKnowledgePoint(request.getKnowledgePoint());
        dto.setTimeLimit(request.getTimeLimit());
        dto.setUploadRequired(false);
        if (request.getOptions() != null) {
            dto.setOptions(request.getOptions().stream().map(option -> {
                QuestionUpsertDTO.OptionDTO converted = new QuestionUpsertDTO.OptionDTO();
                converted.setKey(option.getKey());
                converted.setText(option.getText());
                return converted;
            }).toList());
        }
        QuestionDTO created = create(teacherId, dto);
        return mapper.selectById(created.getId());
    }

    private QuestionDTO toDTO(QuestionBankItem item) {
        return QuestionDTO.builder()
                .id(item.getId())
                .type(item.getType())
                .title(item.getTitle())
                .requirement(item.getRequirement())
                .options(readOptions(item.getOptions()))
                .correctKey(item.getCorrectKey())
                .explanation(item.getExplanation())
                .knowledgePoint(item.getKnowledgePoint())
                .difficulty(item.getDifficulty())
                .timeLimit(item.getDefaultTimeLimit())
                .score(item.getScore())
                .uploadRequired(item.getUploadRequired())
                .sourceDocId(item.getSourceDocId())
                .aiGenerated(item.getAiGenerated())
                .archived(item.getArchived())
                .createdAt(item.getCreatedAt())
                .updatedAt(item.getUpdatedAt())
                .build();
    }

    private void validate(QuestionBankItem item) {
        String type = normalizeType(item.getType());
        item.setType(type);
        if (trimToNull(item.getTitle()) == null) {
            throw new BusinessException(ErrorCode.PARAM_ERROR.getCode(), "题目内容不能为空");
        }
        if (item.getDefaultTimeLimit() != null
                && (item.getDefaultTimeLimit() < 1 || item.getDefaultTimeLimit() > 1800)) {
            throw new BusinessException(ErrorCode.PARAM_ERROR.getCode(), "作答时间应在1到1800秒之间");
        }
        if (item.getDifficulty() != null && !DIFFICULTIES.contains(item.getDifficulty())) {
            throw new BusinessException(ErrorCode.PARAM_ERROR.getCode(), "题目难度无效");
        }
        if ("CHOICE".equals(type)) {
            List<QuestionDTO.OptionDTO> options = readOptions(item.getOptions());
            if (options == null || options.size() < 2) {
                throw new BusinessException(ErrorCode.PARAM_ERROR.getCode(), "选择题至少需要两个选项");
            }
            String correctKey = trimToNull(item.getCorrectKey());
            if (correctKey != null && options.stream().noneMatch(option ->
                    option.getKey() != null && option.getKey().equalsIgnoreCase(correctKey))) {
                throw new BusinessException(ErrorCode.PARAM_ERROR.getCode(), "正确答案必须对应一个选项");
            }
        }
    }

    private String writeOptions(List<QuestionUpsertDTO.OptionDTO> options) {
        if (options == null || options.isEmpty()) return null;
        try {
            return objectMapper.writeValueAsString(options);
        } catch (JsonProcessingException e) {
            throw new BusinessException(ErrorCode.PARAM_ERROR.getCode(), "选项格式错误");
        }
    }

    private List<QuestionDTO.OptionDTO> readOptions(String json) {
        if (json == null || json.isBlank()) return null;
        try {
            return objectMapper.readValue(json, objectMapper.getTypeFactory()
                    .constructCollectionType(List.class, QuestionDTO.OptionDTO.class));
        } catch (JsonProcessingException e) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR.getCode(), "题目选项数据损坏");
        }
    }

    private String normalizeType(String value) {
        String normalized = normalizeNullable(value);
        if (normalized == null) return "HOMEWORK";
        if (!TYPES.contains(normalized)) {
            throw new BusinessException(ErrorCode.PARAM_ERROR.getCode(), "不支持的题型");
        }
        return normalized;
    }

    private String normalizeDifficulty(String value) {
        String normalized = trimToNull(value);
        if (normalized == null) return null;
        normalized = normalized.toLowerCase(Locale.ROOT);
        if (!DIFFICULTIES.contains(normalized)) {
            throw new BusinessException(ErrorCode.PARAM_ERROR.getCode(), "题目难度无效");
        }
        return normalized;
    }

    private String normalizeNullable(String value) {
        String normalized = trimToNull(value);
        return normalized != null ? normalized.toUpperCase(Locale.ROOT) : null;
    }

    private String trimToNull(String value) {
        if (value == null) return null;
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private void requireTeacher(Long teacherId) {
        if (teacherId == null) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED.getCode(), "未登录");
        }
    }
}
