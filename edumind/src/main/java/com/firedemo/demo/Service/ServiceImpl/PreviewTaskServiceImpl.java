package com.firedemo.demo.Service.ServiceImpl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.firedemo.demo.DTO.PreviewTaskDTO;
import com.firedemo.demo.Entity.ClassInfo;
import com.firedemo.demo.Entity.PreviewTask;
import com.firedemo.demo.Service.OpenClawService;
import com.firedemo.demo.Service.PreviewTaskService;
import com.firedemo.demo.common.exception.BusinessException;
import com.firedemo.demo.common.exception.ErrorCode;
import com.firedemo.demo.live.service.LiveNotificationService;
import com.firedemo.demo.mapper.ClassInfoMapper;
import com.firedemo.demo.mapper.PreviewTaskMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class PreviewTaskServiceImpl implements PreviewTaskService {

    private final PreviewTaskMapper mapper;
    private final ClassInfoMapper classInfoMapper;
    private final OpenClawService openClawService;
    private final ObjectMapper objectMapper;
    private final LiveNotificationService liveNotificationService;

    @Override
    @Transactional
    public PreviewTaskDTO createPreviewTask(Long teacherId, Long classId, String knowledgePoint, String topic, String sourceDocId) {
        ClassInfo classInfo = classInfoMapper.selectById(classId);
        if (classInfo == null)
            throw new BusinessException(ErrorCode.DATA_NOT_FOUND.getCode(), "班级不存在");

        String title = topic != null && !topic.isBlank()
                ? topic : "预习：" + knowledgePoint;

        // ── 调用 AI 生成全部内容 ──
        String raw = generateWithAI(knowledgePoint, classInfo.getName());
        Map<String, Object> aiResult = parseAIResult(raw);

        String guideText = (String) aiResult.getOrDefault("guide", "");
        String discussion = (String) aiResult.getOrDefault("discussion", "");

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> questionsRaw = (List<Map<String, Object>>) aiResult.get("questions");
        String questionsJson = toJson(questionsRaw);

        PreviewTask task = PreviewTask.builder()
                .classId(classId).teacherId(teacherId).title(title)
                .knowledgePoint(knowledgePoint).guideText(guideText)
                .questionsJson(questionsJson).discussionQuestion(discussion)
                .sourceDocId(sourceDocId)
                .status("ACTIVE").build();
        mapper.insert(task);

        log.info("预习任务已创建: id={}, classId={}, knowledgePoint={}", task.getId(), classId, knowledgePoint);

        // ── OneBot QQ 群推送 ──
        try {
            liveNotificationService.notifyPreviewTaskPublished(task, classInfo);
        } catch (Exception e) {
            log.warn("预习任务QQ推送失败: taskId={}, {}", task.getId(), e.getMessage());
        }

        return toDTO(task);
    }

    @Override
    public List<PreviewTaskDTO> listByClassId(Long classId) {
        return mapper.findByClassId(classId).stream().map(this::toDTO).collect(Collectors.toList());
    }

    @Override
    public PreviewTaskDTO getById(Long taskId) {
        PreviewTask task = mapper.findById(taskId);
        if (task == null) throw new BusinessException(ErrorCode.DATA_NOT_FOUND.getCode(), "预习任务不存在");
        return toDTO(task);
    }

    @Override
    @Transactional
    public void closeTask(Long taskId, Long teacherId) {
        PreviewTask task = mapper.findById(taskId);
        if (task == null) throw new BusinessException(ErrorCode.DATA_NOT_FOUND.getCode(), "预习任务不存在");
        mapper.closeTask(taskId);
        log.info("预习任务已关闭: id={}", taskId);
    }

    // ── AI 生成 ──

    private String generateWithAI(String knowledgePoint, String className) {
        String prompt = String.format(
                "你是一位资深教师，正在为「%s」班级准备课前预习材料。知识点：%s\n\n" +
                "请生成以下内容，严格按JSON格式输出（不要markdown代码块）：\n" +
                "{\n" +
                "  \"guide\": \"5分钟导读材料（Markdown格式，用问题引导，包含1-2个生动例子，200-400字）\",\n" +
                "  \"questions\": [\n" +
                "    {\"question\":\"选择题题目\",\"options\":[{\"key\":\"A\",\"text\":\"...\"},{\"key\":\"B\",\"text\":\"...\"},{\"key\":\"C\",\"text\":\"...\"},{\"key\":\"D\",\"text\":\"...\"}],\"correctKey\":\"A\",\"explanation\":\"解析\"},\n" +
                "    {\"question\":\"选择题题目2\",\"options\":[...],\"correctKey\":\"B\",\"explanation\":\"解析\"},\n" +
                "    {\"question\":\"简答题题目\",\"options\":null,\"correctKey\":\"参考答案要点\",\"explanation\":\"评分要点\"}\n" +
                "  ],\n" +
                "  \"discussion\": \"1个课堂讨论题，激发学生思考和讨论（50-100字）\"\n" +
                "}\n\n" +
                "要求：题目难度适中，覆盖知识点的核心概念；选项要有干扰性；解析要清晰易懂。",
                className, knowledgePoint);

        String raw = openClawService.chat(prompt, "preview_gen");
        if (raw == null || raw.isBlank())
            throw new BusinessException(ErrorCode.SYSTEM_ERROR.getCode(), "AI生成失败，请重试");
        return raw;
    }

    private Map<String, Object> parseAIResult(String raw) {
        try {
            String json = extractJson(raw);
            return objectMapper.readValue(json, new TypeReference<Map<String, Object>>() {});
        } catch (Exception e) {
            log.error("解析AI预习任务结果失败: {}", e.getMessage());
            // 降级：返回基础内容
            return Map.of(
                    "guide", "请预习「" + "相关知识点" + "」相关内容，思考核心概念和应用场景。",
                    "questions", List.of(),
                    "discussion", "你对这个知识点有什么疑问？请带着问题来上课。"
            );
        }
    }

    private String extractJson(String raw) {
        if (raw == null) return "{}";
        String s = raw.trim();
        int start = s.indexOf('{');
        int end = s.lastIndexOf('}');
        if (start >= 0 && end > start) return s.substring(start, end + 1);
        return s;
    }

    private String toJson(Object obj) {
        try {
            return obj != null ? objectMapper.writeValueAsString(obj) : "[]";
        } catch (JsonProcessingException e) {
            return "[]";
        }
    }

    // ── DTO 转换 ──

    private PreviewTaskDTO toDTO(PreviewTask task) {
        List<PreviewTaskDTO.QuestionItem> questions = List.of();
        if (task.getQuestionsJson() != null && !task.getQuestionsJson().isEmpty()) {
            try {
                questions = objectMapper.readValue(task.getQuestionsJson(),
                        new TypeReference<List<PreviewTaskDTO.QuestionItem>>() {});
            } catch (Exception ignored) {}
        }

        return PreviewTaskDTO.builder()
                .id(task.getId()).classId(task.getClassId())
                .title(task.getTitle()).knowledgePoint(task.getKnowledgePoint())
                .guideText(task.getGuideText()).questions(questions)
                .discussionQuestion(task.getDiscussionQuestion())
                .status(task.getStatus())
                .createdAt(task.getCreatedAt() != null
                        ? task.getCreatedAt().format(DateTimeFormatter.ofPattern("MM-dd HH:mm")) : null)
                .build();
    }
}
