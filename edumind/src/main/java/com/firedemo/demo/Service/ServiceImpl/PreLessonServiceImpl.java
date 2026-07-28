package com.firedemo.demo.Service.ServiceImpl;

import com.firedemo.demo.DTO.PreLessonDTO;
import com.firedemo.demo.DTO.DashboardMetricsDTO;
import com.firedemo.demo.DTO.FrequentErrorDTO;
import com.firedemo.demo.DTO.KnowledgeMasteryDTO;
import com.firedemo.demo.DTO.StudentOverviewDTO;
import com.firedemo.demo.Entity.AiSuggestionCache;
import com.firedemo.demo.Entity.ClassInfo;
import com.firedemo.demo.Entity.ClassroomSession;
import com.firedemo.demo.Service.DashboardService;
import com.firedemo.demo.Service.OpenClawService;
import com.firedemo.demo.Service.PreLessonService;
import com.firedemo.demo.mapper.AiSuggestionCacheMapper;
import com.firedemo.demo.mapper.ClassInfoMapper;
import com.firedemo.demo.mapper.ClassroomSessionMapper;
import com.firedemo.demo.mapper.InteractionMapper;
import com.firedemo.demo.mapper.InteractionResponseMapper;
import com.firedemo.demo.Entity.Interaction;
import com.firedemo.demo.Entity.InteractionResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class PreLessonServiceImpl implements PreLessonService {

    private final DashboardService dashboardService;
    private final ClassInfoMapper classInfoMapper;
    private final ClassroomSessionMapper sessionMapper;
    private final InteractionMapper interactionMapper;
    private final InteractionResponseMapper responseMapper;
    private final OpenClawService openClawService;
    private final AiSuggestionCacheMapper suggestionCacheMapper;

    @Override
    public PreLessonDTO getPreLessonOverview(Long classId) {
        ClassInfo classInfo = classInfoMapper.selectById(classId);
        String className = classInfo != null ? classInfo.getName() : "未知班级";

        // ── 1. 作业维度 ──
        DashboardMetricsDTO metrics = dashboardService.getMetrics(classId);
        List<FrequentErrorDTO> errors = dashboardService.getFrequentErrors(classId, null);
        List<KnowledgeMasteryDTO> masteryList = dashboardService.getKnowledgeMastery(classId);
        List<StudentOverviewDTO> students = dashboardService.getStudentOverview(classId, "score", null);

        // 薄弱知识点 TOP5
        List<PreLessonDTO.WeakPoint> weakPoints = masteryList.stream()
                .filter(m -> m.getMastery() < 80)
                .sorted((a, b) -> Integer.compare(a.getMastery(), b.getMastery()))
                .limit(5)
                .map(m -> PreLessonDTO.WeakPoint.builder()
                        .name(m.getName())
                        .errorCount(m.getErrorCount())
                        .mastery(m.getMastery())
                        .severity(m.getMastery() < 50 ? "critical" : m.getMastery() < 70 ? "high" : "medium")
                        .build())
                .collect(Collectors.toList());

        // ── 2. 课堂互动维度 ──
        List<ClassroomSession> sessions = sessionMapper.findByClassId(classId);
        int liveSessionCount = sessions.size();
        double liveAvgCorrectRate = 0;
        int totalAnswers = 0, totalCorrect = 0;
        for (ClassroomSession session : sessions) {
            List<Interaction> interactions = interactionMapper.findBySessionId(session.getId());
            for (Interaction interaction : interactions) {
                List<InteractionResponse> responses = responseMapper.findByInteractionId(interaction.getId());
                totalAnswers += responses.size();
                totalCorrect += responses.stream().filter(r -> Boolean.TRUE.equals(r.getIsCorrect())).count();
            }
        }
        if (totalAnswers > 0) {
            liveAvgCorrectRate = Math.round(totalCorrect * 1000.0 / totalAnswers) / 10.0;
        }
        int totalInteractions = sessions.stream()
                .mapToInt(s -> interactionMapper.findBySessionId(s.getId()).size()).sum();
        double participationRate = totalInteractions > 0 && metrics.getTotalStudents() > 0
                ? Math.round(totalAnswers * 1000.0 / (totalInteractions * metrics.getTotalStudents())) / 10.0
                : 0;

        // ── 3. 分层分组 ──
        List<PreLessonDTO.TierGroup> tieredGroups = buildTieredGroups(students);

        // ── 4. AI 备课建议（优先缓存，无缓存时返规则版） ──
        AiSuggestionCache cached = suggestionCacheMapper.selectById(classId);
        String aiSuggestion;
        if (cached != null && cached.getSuggestion() != null && !cached.getSuggestion().isBlank()) {
            aiSuggestion = cached.getSuggestion();
        } else {
            aiSuggestion = buildFallback(weakPoints);
        }

        return PreLessonDTO.builder()
                .classId(classId).className(className)
                .avgScore(metrics.getAvgScore())
                .totalStudents(metrics.getTotalStudents())
                .warningCount(metrics.getWarningStudents())
                .weakPoints(weakPoints)
                .liveSessionCount(liveSessionCount)
                .liveAvgCorrectRate(liveAvgCorrectRate)
                .participationRate(participationRate)
                .tieredGroups(tieredGroups)
                .aiSuggestion(aiSuggestion)
                .build();
    }

    private List<PreLessonDTO.TierGroup> buildTieredGroups(List<StudentOverviewDTO> students) {
        int tierA = 0, tierB = 0, tierC = 0;
        for (StudentOverviewDTO s : students) {
            if (s.getAvgScore() >= 80) tierA++;
            else if (s.getAvgScore() >= 60) tierB++;
            else tierC++;
        }
        List<PreLessonDTO.TierGroup> groups = new ArrayList<>();
        if (tierA > 0) groups.add(PreLessonDTO.TierGroup.builder()
                .label("A层（优秀）").range("80-100分").count(tierA)
                .suggestion("侧重拓展提升，布置挑战性综合题，培养知识迁移和创新能力").build());
        if (tierB > 0) groups.add(PreLessonDTO.TierGroup.builder()
                .label("B层（中等）").range("60-79分").count(tierB)
                .suggestion("夯实基础的同时适度拔高，重点攻克高频错题对应的知识点").build());
        if (tierC > 0) groups.add(PreLessonDTO.TierGroup.builder()
                .label("C层（需关注）").range("60分以下").count(tierC)
                .suggestion("回归基础，针对薄弱知识点进行一对一辅导，降低练习难度建立信心").build());
        return groups;
    }

    private String generateSuggestion(String className, DashboardMetricsDTO metrics,
                                       List<PreLessonDTO.WeakPoint> weakPoints,
                                       int liveSessions, double liveCorrectRate,
                                       List<StudentOverviewDTO> students) {
        if (weakPoints.isEmpty() && metrics.getWarningStudents() == 0) {
            return "班级「" + className + "」整体表现良好，建议按正常进度备课，可适当增加拓展内容。";
        }

        StringBuilder ctx = new StringBuilder();
        ctx.append("班级：").append(className).append("\n");
        ctx.append("学生总数：").append(metrics.getTotalStudents())
                .append("，平均分：").append(metrics.getAvgScore())
                .append("，需关注学生：").append(metrics.getWarningStudents()).append("人\n");

        if (!weakPoints.isEmpty()) {
            ctx.append("薄弱知识点：");
            ctx.append(weakPoints.stream()
                    .map(w -> w.getName() + "(掌握度" + w.getMastery() + "%)")
                    .collect(Collectors.joining("、")));
            ctx.append("\n");
        }

        if (liveSessions > 0) {
            ctx.append("历史课堂互动").append(liveSessions).append("次，平均正确率").append(liveCorrectRate).append("%\n");
        }

        String prompt = "你是一位资深教学顾问。根据以下班级数据，给出3-5条具体可操作的备课建议" +
                "（每条30-50字，用序号列出，重点针对薄弱知识点给出教学策略）。直接输出建议，不要开场白。\n\n" + ctx;

        try {
            String raw = openClawService.chat(prompt, "preLesson_" + metrics.hashCode());
            // 清理 markdown 标记
            return raw != null ? raw.replaceAll("^```[\\s\\S]*?\\n|```$", "").trim() : buildFallback(weakPoints);
        } catch (Exception e) {
            log.warn("AI备课建议生成失败: {}", e.getMessage());
            return buildFallback(weakPoints);
        }
    }

    @Override
    public String getAiSuggestion(Long classId) {
        ClassInfo classInfo = classInfoMapper.selectById(classId);
        String className = classInfo != null ? classInfo.getName() : "未知班级";
        DashboardMetricsDTO metrics = dashboardService.getMetrics(classId);
        List<KnowledgeMasteryDTO> masteryList = dashboardService.getKnowledgeMastery(classId);
        List<StudentOverviewDTO> students = dashboardService.getStudentOverview(classId, "score", null);

        List<PreLessonDTO.WeakPoint> weakPoints = masteryList.stream()
                .filter(m -> m.getMastery() < 80)
                .sorted((a, b) -> Integer.compare(a.getMastery(), b.getMastery()))
                .limit(5)
                .map(m -> PreLessonDTO.WeakPoint.builder()
                        .name(m.getName()).errorCount(m.getErrorCount())
                        .mastery(m.getMastery())
                        .severity(m.getMastery() < 50 ? "critical" : m.getMastery() < 70 ? "high" : "medium")
                        .build())
                .collect(Collectors.toList());

        List<ClassroomSession> sessions = sessionMapper.findByClassId(classId);
        int liveSessions = sessions.size();
        double liveCorrectRate = 0;
        int totalAnswers = 0, totalCorrect = 0;
        for (ClassroomSession s : sessions) {
            for (Interaction i : interactionMapper.findBySessionId(s.getId())) {
                List<InteractionResponse> responses = responseMapper.findByInteractionId(i.getId());
                totalAnswers += responses.size();
                totalCorrect += responses.stream().filter(r -> Boolean.TRUE.equals(r.getIsCorrect())).count();
            }
        }
        if (totalAnswers > 0) liveCorrectRate = Math.round(totalCorrect * 1000.0 / totalAnswers) / 10.0;

        String suggestion = generateSuggestion(className, metrics, weakPoints, liveSessions, liveCorrectRate, students);

        // 持久化缓存
        suggestionCacheMapper.upsert(AiSuggestionCache.builder()
                .classId(classId)
                .suggestion(suggestion)
                .build());

        return suggestion;
    }

    // ==================== private helpers ====================

    private String buildFallback(List<PreLessonDTO.WeakPoint> weakPoints) {
        if (weakPoints.isEmpty()) return "暂无特别建议，班级整体表现良好。";
        return "1. 建议重点复习以下知识点：" +
                weakPoints.stream().map(PreLessonDTO.WeakPoint::getName).collect(Collectors.joining("、")) +
                "\n2. 针对薄弱环节设计专项练习\n3. 关注落后学生，提供个别辅导";
    }
}
