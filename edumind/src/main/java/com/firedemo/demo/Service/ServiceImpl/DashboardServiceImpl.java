package com.firedemo.demo.Service.ServiceImpl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.firedemo.demo.DTO.*;
import com.firedemo.demo.Entity.ClassInfo;
import com.firedemo.demo.Entity.Submission;
import com.firedemo.demo.Entity.SubmissionError;
import com.firedemo.demo.Entity.TeacherKnowledge;
import com.firedemo.demo.Entity.User;
import com.firedemo.demo.Service.DashboardService;
import com.firedemo.demo.Service.OpenClawService;
import com.firedemo.demo.common.util.JsonUtil;
import com.firedemo.demo.mapper.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.CacheConfig;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cache.annotation.Caching;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.stream.Collectors;

import jakarta.annotation.PostConstruct;

@Slf4j
@Service
@RequiredArgsConstructor
@CacheConfig(cacheNames = "dashboard")
public class DashboardServiceImpl implements DashboardService {

    /** 从 concept-keywords.properties 加载的概念关键词映射 */
    private final Map<String, String> conceptKeywords = new HashMap<>();

    private final HomeworkEvaluationMapper evaluationMapper;
    private final UserMapper userMapper;
    private final ClassInfoMapper classInfoMapper;
    private final SubmissionMapper submissionMapper;
    private final TeacherKnowledgeMapper teacherKnowledgeMapper;
    private final SubmissionErrorMapper submissionErrorMapper;
    private final OpenClawService openClawService;
    private final ObjectMapper objectMapper;
    private final CacheManager cacheManager;

    @PostConstruct
    void loadConceptKeywords() {
        Properties props = new Properties();
        try (InputStream in = getClass().getClassLoader()
                .getResourceAsStream("prompts/concept-keywords.properties")) {
            if (in != null) {
                props.load(new InputStreamReader(in, StandardCharsets.UTF_8));
                props.forEach((k, v) -> conceptKeywords.put((String) k, (String) v));
                log.info("Loaded {} concept keywords", conceptKeywords.size());
            } else {
                log.warn("concept-keywords.properties not found");
            }
        } catch (Exception e) {
            log.error("Failed to load concept-keywords.properties", e);
        }
    }

    // ======================== Core Metrics ========================

    @Override
    @Cacheable(key = "'metrics:' + #classId", sync = true)
    public DashboardMetricsDTO getMetrics(Long classId) {
        DashboardMetricsDTO metrics = new DashboardMetricsDTO();
        Integer studentCount = submissionMapper.countDistinctStudentsByClassId(classId);
        metrics.setTotalStudents(studentCount != null ? studentCount : 0);
        metrics.setStudentTrend(0);

        Integer totalHomework = evaluationMapper.countByClassId(classId);
        Integer submissionCount = submissionMapper.countByClassId(classId);
        metrics.setTotalHomework((totalHomework != null ? totalHomework : 0) + (submissionCount != null ? submissionCount : 0));

        LocalDateTime weekAgo = LocalDateTime.now().minusDays(7);
        Integer newHomework = evaluationMapper.countNewByClassId(classId, weekAgo);
        Integer newSubmission = submissionMapper.countNewByClassId(classId, weekAgo);
        metrics.setNewHomework((newHomework != null ? newHomework : 0) + (newSubmission != null ? newSubmission : 0));

        List<Integer> evalScores = evaluationMapper.selectScoresByClassId(classId);
        List<Integer> submissionScores = submissionMapper.selectScoresByClassId(classId);
        List<Integer> allScores = new ArrayList<>(evalScores);
        allScores.addAll(submissionScores);

        if (!allScores.isEmpty()) {
            double avgScore = allScores.stream().mapToInt(Integer::intValue).average().orElse(0.0);
            metrics.setAvgScore(Math.round(avgScore * 10) / 10.0);
            metrics.setScoreTrend(0.0);
        } else {
            metrics.setAvgScore(0.0);
            metrics.setScoreTrend(0.0);
        }

        // 需关注学生：按人去重统计（均分<60），而非按作业次数统计
        int warningCount = countDistinctWarningStudents(classId);
        metrics.setWarningStudents(warningCount);
        return metrics;
    }

    // ======================== Score Distribution ========================

    @Override
    @Cacheable(key = "'scoreDist:' + #classId", sync = true)
    public List<ScoreDistributionDTO> getScoreDistribution(Long classId) {
        List<Map<String, Object>> studentOverview = submissionMapper.selectStudentOverviewByClassId(classId);
        int[] ranges = new int[5];
        String[] labels = {"90-100分", "80-89分", "70-79分", "60-69分", "60分以下"};
        String[] colors = {"#52c41a", "#73d13d", "#faad14", "#fa8c16", "#f5222d"};

        for (Map<String, Object> student : studentOverview) {
            int avgScore = student.get("avg_score") instanceof Number
                    ? ((Number) student.get("avg_score")).intValue() : 0;
            if (avgScore >= 90) ranges[0]++;
            else if (avgScore >= 80) ranges[1]++;
            else if (avgScore >= 70) ranges[2]++;
            else if (avgScore >= 60) ranges[3]++;
            else ranges[4]++;
        }

        int total = studentOverview.size();
        List<ScoreDistributionDTO> result = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            ScoreDistributionDTO dto = new ScoreDistributionDTO();
            dto.setRange(labels[i]);
            dto.setCount(ranges[i]);
            dto.setPercentage(total > 0 ? Math.round(ranges[i] * 100.0 / total * 10) / 10.0 : 0.0);
            dto.setColor(colors[i]);
            result.add(dto);
        }
        return result;
    }

    // ======================== Knowledge Heatmap ========================

    @Override
    @Cacheable(key = "'knowledge:' + #classId", sync = true)
    public List<KnowledgeMasteryDTO> getKnowledgeMastery(Long classId) {
        List<TeacherKnowledge> teacherKps = teacherKnowledgeMapper.selectByClassId(classId);

        List<Map<String, Object>> errorStats = submissionErrorMapper.selectErrorStatsByClassId(classId);
        Map<String, Map<String, Object>> statsMap = new HashMap<>();
        for (Map<String, Object> row : errorStats) {
            statsMap.put((String) row.get("knowledge_point"), row);
        }

        Integer totalSubmissions = submissionMapper.countByClassId(classId);
        int total = totalSubmissions != null ? totalSubmissions : 1;

        List<KnowledgeMasteryDTO> result = new ArrayList<>();
        boolean hasOtherInTk = teacherKps.stream().anyMatch(tk -> "其他".equals(tk.getName()));

        if (!teacherKps.isEmpty()) {
            for (TeacherKnowledge tk : teacherKps) {
                Map<String, Object> stats = statsMap.get(tk.getName());
                KnowledgeMasteryDTO dto = buildMasteryDTO(
                        tk.getId(), tk.getName(), stats, total,
                        tk.getColor() != null ? tk.getColor() : "#1890ff");
                result.add(dto);
            }
            if (!hasOtherInTk && statsMap.containsKey("其他")) {
                KnowledgeMasteryDTO other = buildMasteryDTO(null, "其他", statsMap.get("其他"), total, "#bfbfbf");
                result.add(other);
            }
        } else {
            KnowledgeMasteryDTO other = buildMasteryDTO(null, "其他", statsMap.get("其他"), total, "#bfbfbf");
            if (other.getErrorCount() == 0) {
                other = new KnowledgeMasteryDTO(null, "其他", 100, 0, 0, "#bfbfbf");
            }
            result.add(other);
        }
        return result;
    }

    private KnowledgeMasteryDTO buildMasteryDTO(Long id, String name, Map<String, Object> stats, int totalSubmissions, String color) {
        int errorCount = 0, criticalCount = 0;
        if (stats != null) {
            errorCount = stats.get("error_count") instanceof Number ? ((Number) stats.get("error_count")).intValue() : 0;
            criticalCount = stats.get("critical_count") instanceof Number ? ((Number) stats.get("critical_count")).intValue() : 0;
        }
        int mastery = Math.max(0, 100 - (int) Math.round(errorCount * 100.0 / totalSubmissions));
        return new KnowledgeMasteryDTO(id, name, mastery, errorCount, criticalCount, color);
    }

    // ======================== Frequent Errors ========================

    @Override
    public List<FrequentErrorDTO> getFrequentErrors(Long classId, String knowledgePoint) {
        List<SubmissionError> errors;
        if (knowledgePoint != null && !knowledgePoint.isEmpty() && !"全部".equals(knowledgePoint)) {
            errors = submissionErrorMapper.selectByClassIdAndKnowledgePoint(classId, knowledgePoint, 50);
        } else {
            errors = submissionErrorMapper.selectByClassIdAndKnowledgePoint(classId, null, 50);
        }

        Map<String, ErrorAgg> aggMap = new LinkedHashMap<>();
        for (SubmissionError e : errors) {
            String key = e.getErrorText();
            ErrorAgg agg = aggMap.computeIfAbsent(key, k -> new ErrorAgg());
            agg.count++;
            agg.knowledgePoint = e.getKnowledgePoint();
            String sev = e.getSeverity();
            if (sev != null) {
                if ("critical".equals(sev) || "high".equals(sev)) agg.difficulty = "high";
                else if ("major".equals(sev) || "medium".equals(sev)) {
                    if (!"high".equals(agg.difficulty)) agg.difficulty = "medium";
                } else {
                    if (agg.difficulty == null) agg.difficulty = "low";
                }
            }
        }

        return aggMap.entrySet().stream()
                .sorted(Map.Entry.<String, ErrorAgg>comparingByValue(Comparator.comparingInt(a -> -a.count)))
                .limit(10)
                .map(entry -> {
                    FrequentErrorDTO dto = new FrequentErrorDTO();
                    dto.setQuestion(entry.getKey());
                    String diff = entry.getValue().difficulty != null ? entry.getValue().difficulty : "medium";
                    dto.setDifficulty(diff);
                    dto.setDifficultyLabel(convertDifficultyLabel(diff));
                    dto.setErrorCount(entry.getValue().count);
                    dto.setKnowledgePoint(entry.getValue().knowledgePoint);
                    return dto;
                })
                .collect(Collectors.toList());
    }

    private static class ErrorAgg {
        int count;
        String difficulty;
        String knowledgePoint;
    }

    // ======================== Student Overview ========================

    @Override
    public List<StudentOverviewDTO> getStudentOverview(Long classId, String sortBy, String keyword) {
        List<User> students = userMapper.selectStudentsByClassId(classId);
        List<Map<String, Object>> submissionStudents = submissionMapper.selectStudentOverviewByClassId(classId);
        Map<String, StudentOverviewDTO> studentMap = new LinkedHashMap<>();

        List<Map<String, Object>> teacherStats = evaluationMapper.selectStudentStatsByClassId(classId);
        Map<Long, Map<String, Object>> statsMap = new HashMap<>();
        for (Map<String, Object> row : teacherStats) {
            statsMap.put(((Number) row.get("user_id")).longValue(), row);
        }

        for (User student : students) {
            if (keyword != null && !keyword.isEmpty() && !student.getUsername().contains(keyword)) continue;
            StudentOverviewDTO dto = new StudentOverviewDTO();
            dto.setId(student.getId());
            dto.setName(student.getUsername());
            Map<String, Object> stats = statsMap.get(student.getId());
            if (stats != null) {
                dto.setHomeworkCount(((Number) stats.get("homework_count")).intValue());
                Number avgScore = (Number) stats.get("avg_score");
                dto.setAvgScore(avgScore != null ? (int) Math.round(avgScore.doubleValue()) : 0);
                dto.setErrorCount(0);
                dto.setTrend(0);
            } else {
                dto.setHomeworkCount(0);
                dto.setAvgScore(0);
                dto.setErrorCount(0);
                dto.setTrend(0);
            }
            dto.setNeedAttention(dto.getAvgScore() < 70 || dto.getErrorCount() > 15);
            studentMap.put(student.getUsername(), dto);
        }

        for (Map<String, Object> row : submissionStudents) {
            String studentId = (String) row.get("student_id");
            String name = (String) row.get("student_name");
            if (keyword != null && !keyword.isEmpty() && !name.contains(keyword) && !studentId.contains(keyword)) continue;
            Number count = (Number) row.get("homework_count");
            Number avgScore = (Number) row.get("avg_score");
            String key = studentId != null ? studentId : name;
            if (!studentMap.containsKey(key)) {
                StudentOverviewDTO dto = new StudentOverviewDTO();
                dto.setId(0L);
                dto.setName(name);
                dto.setStudentId(studentId);
                dto.setHomeworkCount(count.intValue());
                dto.setAvgScore(avgScore.intValue());
                dto.setErrorCount(0);
                dto.setTrend(0);
                dto.setNeedAttention(avgScore.intValue() < 70);
                studentMap.put(key, dto);
            }
        }

        List<StudentOverviewDTO> result = new ArrayList<>(studentMap.values());
        switch (sortBy) {
            case "progress": result.sort((a, b) -> b.getTrend() - a.getTrend()); break;
            case "homework": result.sort((a, b) -> b.getHomeworkCount() - a.getHomeworkCount()); break;
            default: result.sort((a, b) -> b.getAvgScore() - a.getAvgScore()); break;
        }
        return result;
    }

    // ======================== Class List ========================

    @Override
    public List<ClassInfoDTO> getClassList(Long teacherId) {
        List<Map<String, Object>> rows = classInfoMapper.selectByTeacherIdWithStudentCount(teacherId);
        List<ClassInfoDTO> result = new ArrayList<>();
        for (Map<String, Object> row : rows) {
            ClassInfoDTO dto = new ClassInfoDTO();
            dto.setId(((Number) row.get("id")).longValue());
            dto.setName((String) row.get("name"));
            Number studentCount = (Number) row.get("student_count");
            dto.setStudentCount(studentCount != null ? studentCount.intValue() : 0);
            result.add(dto);
        }
        return result;
    }

    // ======================== Teacher Knowledge CRUD ========================

    @Override
    public List<TeacherKnowledge> getTeacherKnowledge(Long classId) {
        return teacherKnowledgeMapper.selectByClassId(classId);
    }

    @Override
    @Transactional
    @Caching(evict = {
        @CacheEvict(key = "'knowledge:' + #classId"),
        @CacheEvict(key = "'metrics:' + #classId"),
        @CacheEvict(key = "'scoreDist:' + #classId")
    })
    public void saveTeacherKnowledge(Long classId, Long userId, List<TeacherKnowledgeDTO> items) {
        teacherKnowledgeMapper.delete(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<TeacherKnowledge>()
                        .eq(TeacherKnowledge::getClassId, classId));
        if (items != null && !items.isEmpty()) {
            List<TeacherKnowledge> batch = new ArrayList<>(items.size());
            for (int i = 0; i < items.size(); i++) {
                TeacherKnowledgeDTO dto = items.get(i);
                TeacherKnowledge tk = new TeacherKnowledge();
                tk.setClassId(classId);
                tk.setName(dto.getName());
                tk.setColor(dto.getColor() != null ? dto.getColor() : "#1890ff");
                tk.setSortOrder(dto.getSortOrder() != null ? dto.getSortOrder() : i);
                tk.setCreatedBy(userId);
                batch.add(tk);
            }
            teacherKnowledgeMapper.insertBatch(batch);
        }
        ensureOtherExists(classId, userId);
        reclassifyUnclassified(classId);
        reclassifyWithAI(classId);
    }

    @Override
    @Transactional
    @Caching(evict = {
        @CacheEvict(key = "'knowledge:' + #classId"),
        @CacheEvict(key = "'metrics:' + #classId"),
        @CacheEvict(key = "'scoreDist:' + #classId")
    })
    public void addTeacherKnowledge(Long classId, Long userId, String name, String color) {
        TeacherKnowledge tk = new TeacherKnowledge();
        tk.setClassId(classId);
        tk.setName(name);
        tk.setColor(color != null ? color : "#1890ff");
        tk.setSortOrder(0);
        tk.setCreatedBy(userId);
        teacherKnowledgeMapper.insert(tk);
        ensureOtherExists(classId, userId);
        reclassifyUnclassified(classId);
        reclassifyWithAI(classId);
    }

    @Override
    public void deleteTeacherKnowledge(Long id) {
        TeacherKnowledge tk = teacherKnowledgeMapper.selectById(id);
        if (tk != null) {
            teacherKnowledgeMapper.deleteById(id);
            // 手动驱逐缓存 — 使用 tk.getClassId() 而非错误的 #classId SpEL
            evictDashboardCache(tk.getClassId());
        }
    }

    // ======================== Ensure "Other" + Reclassify ========================

    private void ensureOtherExists(Long classId, Long userId) {
        if (teacherKnowledgeMapper.exists(classId, "其他")) return;
        TeacherKnowledge other = new TeacherKnowledge();
        other.setClassId(classId);
        other.setName("其他");
        other.setColor("#bfbfbf");
        other.setSortOrder(Integer.MAX_VALUE);
        other.setCreatedBy(userId);
        teacherKnowledgeMapper.insert(other);
    }

    private void reclassifyUnclassified(Long classId) {
        List<SubmissionError> unclassified = submissionErrorMapper.selectUnclassifiedByClassId(classId);
        if (unclassified.isEmpty()) return;

        List<TeacherKnowledge> kps = teacherKnowledgeMapper.selectByClassId(classId);
        if (kps.isEmpty()) return;

        Map<String, String> keywordMap = buildKeywordMap(kps);
        LocalDateTime now = LocalDateTime.now();
        List<SubmissionError> toUpdate = new ArrayList<>();
        for (SubmissionError se : unclassified) {
            String match = matchKeyword(se.getErrorText(), keywordMap);
            if (match != null && !"其他".equals(match)) {
                se.setKnowledgePoint(match);
                se.setUpdatedAt(now);
                toUpdate.add(se);
            }
        }
        if (!toUpdate.isEmpty()) {
            for (SubmissionError se : toUpdate) {
                submissionErrorMapper.updateById(se);
            }
            log.info("Keyword reclassify done: classId={}, reclassified={}", classId, toUpdate.size());
        }
    }

    @Async
    void reclassifyWithAI(Long classId) {
        List<SubmissionError> unclassified = submissionErrorMapper.selectUnclassifiedByClassId(classId);
        if (unclassified.isEmpty()) return;

        List<TeacherKnowledge> kps = teacherKnowledgeMapper.selectByClassId(classId);
        if (kps.isEmpty()) return;

        List<String> kpNames = kps.stream().map(TeacherKnowledge::getName).collect(Collectors.toList());

        String prompt = buildReclassifyPrompt(unclassified, kpNames);
        try {
            String response = openClawService.chat(prompt, "reclassify_" + classId);
            String jsonStr = JsonUtil.extractJson(response);
            JsonNode root = objectMapper.readTree(jsonStr);
            JsonNode results = root.get("results");
            if (results == null || !results.isArray()) return;

            int reclassified = 0;
            List<SubmissionError> batch = new ArrayList<>();
            for (JsonNode r : results) {
                int index = r.get("index").asInt();
                String kp = r.get("knowledgePoint").asText();
                if (!"其他".equals(kp) && index >= 0 && index < unclassified.size()) {
                    SubmissionError se = unclassified.get(index);
                    se.setKnowledgePoint(kp);
                    se.setUpdatedAt(LocalDateTime.now());
                    batch.add(se);
                    reclassified++;
                }
            }
            if (!batch.isEmpty()) {
                for (SubmissionError e : batch) {
                    submissionErrorMapper.updateById(e);
                }
            }
            if (reclassified > 0) {
                log.info("AI reclassify done: classId={}, reclassified={}", classId, reclassified);
            }
        } catch (Exception e) {
            log.warn("AI reclassify failed for classId={}: {}", classId, e.getMessage());
        }
    }

    private String buildReclassifyPrompt(List<SubmissionError> unclassified, List<String> kpNames) {
        StringBuilder sb = new StringBuilder();
        sb.append("现有 C 语言错误列表（共 ").append(unclassified.size()).append(" 条）：\n");
        for (int i = 0; i < unclassified.size(); i++) {
            sb.append(i).append(": \"").append(unclassified.get(i).getErrorText()).append("\"\n");
        }
        sb.append("\n请将每条错误归类到以下知识点之一：\n");
        sb.append(kpNames).append("\n\n");
        sb.append("返回纯 JSON：\n");
        sb.append("{\"results\": [{\"index\":0, \"knowledgePoint\":\"指针\"}, ...]}\n\n");
        sb.append("规则：\n- 不属于任何一个的填\"其他\"\n- 不要输出任何解释");
        return sb.toString();
    }

    private Map<String, String> buildKeywordMap(List<TeacherKnowledge> kps) {
        Map<String, String> result = new LinkedHashMap<>();
        Set<String> kpNames = kps.stream().map(TeacherKnowledge::getName).collect(Collectors.toSet());
        for (Map.Entry<String, String> e : conceptKeywords.entrySet()) {
            if (kpNames.contains(e.getValue())) {
                result.put(e.getKey(), e.getValue());
            }
        }
        for (String name : kpNames) {
            result.putIfAbsent(name, name);
        }
        return result;
    }

    private String matchKeyword(String errorText, Map<String, String> keywordMap) {
        if (errorText == null) return null;
        String lower = errorText.toLowerCase();
        for (Map.Entry<String, String> e : keywordMap.entrySet()) {
            if (lower.contains(e.getKey().toLowerCase())) {
                return e.getValue();
            }
        }
        return null;
    }

    // ======================== Warning Students ========================

    /**
     * 统计需关注的学生数（按人去重，均分 < 60）。
     * 合并 homework_evaluation（按 user_id）和 submission（按 student_id）
     * 两个数据源，使用与 getStudentOverview 相同的合并键避免重复计数。
     */
    private int countDistinctWarningStudents(Long classId) {
        // identity key → 平均分
        Map<String, Double> studentAvgs = new LinkedHashMap<>();

        // 1. homework_evaluation 表：按 user_id 聚合
        List<Map<String, Object>> evalStats = evaluationMapper.selectStudentStatsByClassId(classId);
        Map<Long, Double> userIdAvgMap = new HashMap<>();
        for (Map<String, Object> row : evalStats) {
            long userId = ((Number) row.get("user_id")).longValue();
            Number avgScore = (Number) row.get("avg_score");
            if (avgScore != null) {
                userIdAvgMap.put(userId, avgScore.doubleValue());
            }
        }

        // 将 user_id 映射为 username（与 getStudentOverview 对齐）
        List<User> classStudents = userMapper.selectStudentsByClassId(classId);
        for (User u : classStudents) {
            Double avg = userIdAvgMap.get(u.getId());
            if (avg != null) {
                studentAvgs.put(u.getUsername(), avg);
            }
        }

        // 2. submission 表：按 student_id 聚合（只补充未在 sys_user 中出现的学生）
        List<Map<String, Object>> subStudents = submissionMapper.selectStudentOverviewByClassId(classId);
        for (Map<String, Object> row : subStudents) {
            String studentId = (String) row.get("student_id");
            String name = (String) row.get("student_name");
            String key = (studentId != null && !studentId.isEmpty()) ? studentId : name;
            Number avgScore = (Number) row.get("avg_score");
            if (avgScore != null && !studentAvgs.containsKey(key)) {
                studentAvgs.put(key, avgScore.doubleValue());
            }
        }

        // 统计均分 < 60 的学生数
        return (int) studentAvgs.values().stream().filter(avg -> avg < 60).count();
    }

    // ======================== Utils ========================

    /**
     * 统一驱逐 classId 相关的所有仪表盘缓存
     */
    private void evictDashboardCache(Long classId) {
        if (classId == null) return;
        var cache = cacheManager.getCache("dashboard");
        if (cache != null) {
            cache.evict("knowledge:" + classId);
            cache.evict("metrics:" + classId);
            cache.evict("scoreDist:" + classId);
        }
    }

    private String convertDifficultyLabel(String priority) {
        switch (priority) {
            case "high":
            case "critical": return "困难";
            case "medium": return "中等";
            default: return "简单";
        }
    }
}
