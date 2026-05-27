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
import com.firedemo.demo.mapper.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheConfig;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
@CacheConfig(cacheNames = "dashboard")
public class DashboardServiceImpl implements DashboardService {

    private final HomeworkEvaluationMapper evaluationMapper;
    private final UserMapper userMapper;
    private final ClassInfoMapper classInfoMapper;
    private final SubmissionMapper submissionMapper;
    private final TeacherKnowledgeMapper teacherKnowledgeMapper;
    private final SubmissionErrorMapper submissionErrorMapper;
    private final OpenClawService openClawService;
    private final ObjectMapper objectMapper;

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
            metrics.setScoreTrend(3.2);
            long warningCount = allScores.stream().filter(score -> score < 60).count();
            metrics.setWarningStudents((int) warningCount);
        } else {
            metrics.setAvgScore(0.0);
            metrics.setScoreTrend(0.0);
            metrics.setWarningStudents(0);
        }
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
        Random random = new Random();

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
                dto.setTrend(random.nextInt(10) - 5);
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
        List<ClassInfo> classes = classInfoMapper.selectByTeacherId(teacherId);
        List<ClassInfoDTO> result = new ArrayList<>();
        for (ClassInfo cls : classes) {
            ClassInfoDTO dto = new ClassInfoDTO();
            dto.setId(cls.getId());
            dto.setName(cls.getName());
            Integer count = userMapper.countStudentsByClassId(cls.getId());
            dto.setStudentCount(count != null ? count : 0);
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
    @CacheEvict(key = "'knowledge:' + #classId")
    public void saveTeacherKnowledge(Long classId, Long userId, List<TeacherKnowledgeDTO> items) {
        teacherKnowledgeMapper.delete(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<TeacherKnowledge>()
                        .eq(TeacherKnowledge::getClassId, classId));
        if (items == null || items.isEmpty()) return;
        for (int i = 0; i < items.size(); i++) {
            TeacherKnowledgeDTO dto = items.get(i);
            TeacherKnowledge tk = new TeacherKnowledge();
            tk.setClassId(classId);
            tk.setName(dto.getName());
            tk.setColor(dto.getColor() != null ? dto.getColor() : "#1890ff");
            tk.setSortOrder(dto.getSortOrder() != null ? dto.getSortOrder() : i);
            tk.setCreatedBy(userId);
            teacherKnowledgeMapper.insert(tk);
        }
        ensureOtherExists(classId, userId);
        reclassifyUnclassified(classId);
        reclassifyWithAI(classId);
    }

    @Override
    @Transactional
    @CacheEvict(key = "'knowledge:' + #classId")
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
    @CacheEvict(key = "'knowledge:' + #classId")
    public void deleteTeacherKnowledge(Long id) {
        TeacherKnowledge tk = teacherKnowledgeMapper.selectById(id);
        if (tk != null) {
            teacherKnowledgeMapper.deleteById(id);
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
        int reclassified = 0;
        for (SubmissionError se : unclassified) {
            String match = matchKeyword(se.getErrorText(), keywordMap);
            if (match != null && !"其他".equals(match)) {
                se.setKnowledgePoint(match);
                se.setUpdatedAt(LocalDateTime.now());
                submissionErrorMapper.updateById(se);
                reclassified++;
            }
        }
        if (reclassified > 0) {
            log.info("Keyword reclassify done: classId={}, reclassified={}", classId, reclassified);
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
            String jsonStr = extractFirstJson(response);
            JsonNode root = objectMapper.readTree(jsonStr);
            JsonNode results = root.get("results");
            if (results == null || !results.isArray()) return;

            int reclassified = 0;
            for (JsonNode r : results) {
                int index = r.get("index").asInt();
                String kp = r.get("knowledgePoint").asText();
                if (!"其他".equals(kp) && index >= 0 && index < unclassified.size()) {
                    SubmissionError se = unclassified.get(index);
                    se.setKnowledgePoint(kp);
                    se.setUpdatedAt(LocalDateTime.now());
                    submissionErrorMapper.updateById(se);
                    reclassified++;
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
        Map<String, String> conceptMap = new HashMap<>();
        conceptMap.put("指针", "指针");
        conceptMap.put("malloc", "指针");
        conceptMap.put("free", "指针");
        conceptMap.put("calloc", "指针");
        conceptMap.put("内存", "指针");
        conceptMap.put("地址", "指针");
        conceptMap.put("数组", "数组");
        conceptMap.put("下标", "数组");
        conceptMap.put("越界", "数组");
        conceptMap.put("一维", "数组");
        conceptMap.put("二维", "数组");
        conceptMap.put("字符串", "字符数组与字符串");
        conceptMap.put("strlen", "字符数组与字符串");
        conceptMap.put("strcpy", "字符数组与字符串");
        conceptMap.put("strcmp", "字符数组与字符串");
        conceptMap.put("循环", "循环结构");
        conceptMap.put("for", "循环结构");
        conceptMap.put("while", "循环结构");
        conceptMap.put("break", "循环结构");
        conceptMap.put("continue", "循环结构");
        conceptMap.put("嵌套", "循环结构");
        conceptMap.put("函数", "函数");
        conceptMap.put("递归", "函数");
        conceptMap.put("返回值", "函数");
        conceptMap.put("参数", "函数");
        conceptMap.put("变量", "变量与数据类型");
        conceptMap.put("int", "变量与数据类型");
        conceptMap.put("float", "变量与数据类型");
        conceptMap.put("char", "变量与数据类型");
        conceptMap.put("double", "变量与数据类型");
        conceptMap.put("类型", "变量与数据类型");
        conceptMap.put("常量", "变量与数据类型");
        conceptMap.put("scanf", "输入输出");
        conceptMap.put("printf", "输入输出");
        conceptMap.put("输入", "输入输出");
        conceptMap.put("输出", "输入输出");
        conceptMap.put("if", "条件分支");
        conceptMap.put("switch", "条件分支");
        conceptMap.put("else", "条件分支");
        conceptMap.put("条件", "条件分支");
        conceptMap.put("结构体", "结构体");
        conceptMap.put("struct", "结构体");
        conceptMap.put("typedef", "结构体");
        conceptMap.put("文件", "文件操作");
        conceptMap.put("fopen", "文件操作");
        conceptMap.put("fclose", "文件操作");
        conceptMap.put("fprintf", "文件操作");
        conceptMap.put("fscanf", "文件操作");

        Map<String, String> result = new LinkedHashMap<>();
        Set<String> kpNames = kps.stream().map(TeacherKnowledge::getName).collect(Collectors.toSet());
        for (Map.Entry<String, String> e : conceptMap.entrySet()) {
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

    private String extractFirstJson(String response) {
        if (response == null || response.isEmpty()) return "{}";
        String trimmed = response.trim();
        int start = trimmed.indexOf('{');
        int end = trimmed.lastIndexOf('}');
        if (start != -1 && end > start) {
            return trimmed.substring(start, end + 1);
        }
        return response;
    }

    // ======================== Utils ========================

    private String convertDifficultyLabel(String priority) {
        switch (priority) {
            case "high":
            case "critical": return "困难";
            case "medium": return "中等";
            default: return "简单";
        }
    }
}
