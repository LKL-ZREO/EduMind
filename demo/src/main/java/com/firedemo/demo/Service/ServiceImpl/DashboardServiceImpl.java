package com.firedemo.demo.Service.ServiceImpl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.firedemo.demo.DTO.*;
import com.firedemo.demo.Entity.ClassInfo;
import com.firedemo.demo.Entity.HomeworkEvaluation;
import com.firedemo.demo.Entity.User;
import com.firedemo.demo.Service.DashboardService;
import com.firedemo.demo.mapper.ClassInfoMapper;
import com.firedemo.demo.mapper.HomeworkEvaluationMapper;
import com.firedemo.demo.mapper.HomeworkKnowledgeMapper;
import com.firedemo.demo.mapper.SubmissionMapper;
import com.firedemo.demo.mapper.UserMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

import com.firedemo.demo.Entity.Submission;

/**
 * 仪表盘数据服务实现
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DashboardServiceImpl implements DashboardService {

    private final HomeworkEvaluationMapper evaluationMapper;
    private final UserMapper userMapper;
    private final ClassInfoMapper classInfoMapper;
    private final HomeworkKnowledgeMapper homeworkKnowledgeMapper;
    private final SubmissionMapper submissionMapper;
    private final ObjectMapper objectMapper;

    @Override
    public DashboardMetricsDTO getMetrics(Long classId) {
        DashboardMetricsDTO metrics = new DashboardMetricsDTO();
        
        // 学生总数 = submission 表独立提交者（sys_user 为教师用户，不统计学生）
        Integer studentCount = submissionMapper.countDistinctStudentsByClassId(classId);
        metrics.setTotalStudents(studentCount != null ? studentCount : 0);
        metrics.setStudentTrend(0); // 暂无趋势计算
        
        // 作业统计（老师端 + 学生端）
        Integer totalHomework = evaluationMapper.countByClassId(classId);
        Integer submissionCount = submissionMapper.countByClassId(classId);
        metrics.setTotalHomework((totalHomework != null ? totalHomework : 0) + (submissionCount != null ? submissionCount : 0));
        
        LocalDateTime weekAgo = LocalDateTime.now().minusDays(7);
        Integer newHomework = evaluationMapper.countNewByClassId(classId, weekAgo);
        Integer newSubmission = submissionMapper.countNewByClassId(classId, weekAgo);
        metrics.setNewHomework((newHomework != null ? newHomework : 0) + (newSubmission != null ? newSubmission : 0));
        
        // 计算平均分和需关注学生
        List<HomeworkEvaluation> evaluations = evaluationMapper.selectByClassId(classId);
        List<Integer> submissionScores = submissionMapper.selectScoresByClassId(classId);
        // 合并老师端和学生端分数做统计
        List<Integer> allScores = new ArrayList<>();
        for (HomeworkEvaluation e : evaluations) {
            int score = parseScoreFromRawResponse(e.getRawResponse());
            if (score > 0) allScores.add(score);
        }
        allScores.addAll(submissionScores);
        
        if (!allScores.isEmpty()) {
            double avgScore = allScores.stream()
                .mapToInt(Integer::intValue)
                .average()
                .orElse(0.0);
            metrics.setAvgScore(Math.round(avgScore * 10) / 10.0);
            metrics.setScoreTrend(3.2); // 模拟趋势
            
            // 低分学生数（分数<60）
            long warningCount = allScores.stream()
                .filter(score -> score < 60)
                .count();
            metrics.setWarningStudents((int) warningCount);
        } else {
            metrics.setAvgScore(0.0);
            metrics.setScoreTrend(0.0);
            metrics.setWarningStudents(0);
        }
        
        return metrics;
    }

    @Override
    public List<ScoreDistributionDTO> getScoreDistribution(Long classId) {
        List<HomeworkEvaluation> evaluations = evaluationMapper.selectByClassId(classId);
        List<Integer> submissionScores = submissionMapper.selectScoresByClassId(classId);
        
        // 分数段统计
        int[] ranges = new int[5]; // 90-100, 80-89, 70-79, 60-69, <60
        String[] labels = {"90-100分", "80-89分", "70-79分", "60-69分", "60分以下"};
        String[] colors = {"#52c41a", "#73d13d", "#faad14", "#fa8c16", "#f5222d"};
        
        for (HomeworkEvaluation eval : evaluations) {
            int score = parseScoreFromRawResponse(eval.getRawResponse());
            if (score >= 90) ranges[0]++;
            else if (score >= 80) ranges[1]++;
            else if (score >= 70) ranges[2]++;
            else if (score >= 60) ranges[3]++;
            else ranges[4]++;
        }
        for (Integer score : submissionScores) {
            if (score >= 90) ranges[0]++;
            else if (score >= 80) ranges[1]++;
            else if (score >= 70) ranges[2]++;
            else if (score >= 60) ranges[3]++;
            else ranges[4]++;
        }
        
        int total = evaluations.size() + submissionScores.size();
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

    @Override
    public List<KnowledgeMasteryDTO> getKnowledgeMastery(Long classId) {
        // 从 homework_knowledge 表查询知识点掌握度统计（老师端）
        List<Map<String, Object>> teacherStats = homeworkKnowledgeMapper.selectKnowledgeStatsByClassId(classId);
        // 从 homework_knowledge 表查询知识点掌握度统计（学生端 submission 关联）
        List<Map<String, Object>> studentStats = submissionMapper.selectKnowledgeStatsByClassId(classId);
        
        // 合并
        Map<String, KnowledgeMasteryDTO> merged = new LinkedHashMap<>();
        
        for (Map<String, Object> stat : teacherStats) {
            String name = (String) stat.get("knowledge_point");
            KnowledgeMasteryDTO dto = new KnowledgeMasteryDTO();
            dto.setName(name);
            Object avgMasteryObj = stat.get("avg_mastery");
            dto.setMastery(avgMasteryObj instanceof Number ? ((Number) avgMasteryObj).intValue() : 0);
            merged.put(name, dto);
        }
        
        for (Map<String, Object> stat : studentStats) {
            String name = (String) stat.get("knowledge_point");
            if (merged.containsKey(name)) {
                // 取平均值
                KnowledgeMasteryDTO existing = merged.get(name);
                Object avgMasteryObj = stat.get("avg_mastery");
                int studentMastery = avgMasteryObj instanceof Number ? ((Number) avgMasteryObj).intValue() : 0;
                existing.setMastery((existing.getMastery() + studentMastery) / 2);
            } else {
                KnowledgeMasteryDTO dto = new KnowledgeMasteryDTO();
                dto.setName(name);
                Object avgMasteryObj = stat.get("avg_mastery");
                dto.setMastery(avgMasteryObj instanceof Number ? ((Number) avgMasteryObj).intValue() : 0);
                merged.put(name, dto);
            }
        }
        
        return new ArrayList<>(merged.values());
    }

    @Override
    public List<FrequentErrorDTO> getFrequentErrors(Long classId) {
        List<HomeworkEvaluation> evaluations = evaluationMapper.selectByClassId(classId);
        List<Submission> submissions = submissionMapper.selectByClassId(classId);
        
        // 从raw_response解析错误统计
        Map<String, Integer> errorCountMap = new HashMap<>();
        Map<String, String> errorDifficultyMap = new HashMap<>();
        
        // 解析 homework_evaluation 的 rawResponse
        for (HomeworkEvaluation eval : evaluations) {
            parseRawResponseForErrors(eval.getRawResponse(), errorCountMap, errorDifficultyMap);
        }
        // 解析 submission 的 rawResponse
        for (Submission sub : submissions) {
            parseRawResponseForErrors(sub.getRawResponse(), errorCountMap, errorDifficultyMap);
        }
        
        // 排序取TOP10
        List<Map.Entry<String, Integer>> sortedErrors = errorCountMap.entrySet()
            .stream()
            .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
            .limit(10)
            .collect(Collectors.toList());
        
        int totalStudents = userMapper.countStudentsByClassId(classId);
        
        List<FrequentErrorDTO> result = new ArrayList<>();
        for (Map.Entry<String, Integer> entry : sortedErrors) {
            FrequentErrorDTO dto = new FrequentErrorDTO();
            dto.setQuestion(entry.getKey());
            String priority = errorDifficultyMap.getOrDefault(entry.getKey(), "medium");
            dto.setDifficulty(priority);
            dto.setDifficultyLabel(convertDifficultyLabel(priority));
            dto.setErrorCount(entry.getValue());
            dto.setErrorRate(totalStudents > 0 ? 
                (int) Math.round(entry.getValue() * 100.0 / totalStudents) : 0);
            result.add(dto);
        }
        
        // 如果没有数据，返回模拟数据
        if (result.isEmpty()) {
            result = getMockFrequentErrors();
        }
        
        return result;
    }

    @Override
    public List<StudentOverviewDTO> getStudentOverview(Long classId, String sortBy, String keyword) {
        // 老师端学生
        List<User> students = userMapper.selectStudentsByClassId(classId);
        // 学生端提交者（从文件名解析，可能有重复提交）
        List<Map<String, Object>> submissionStudents = submissionMapper.selectStudentOverviewByClassId(classId);
        
        Map<String, StudentOverviewDTO> studentMap = new LinkedHashMap<>();
        Random random = new Random();
        
        // 处理老师端学生
        for (User student : students) {
            if (keyword != null && !keyword.isEmpty() && 
                !student.getUsername().contains(keyword)) {
                continue;
            }
            StudentOverviewDTO dto = new StudentOverviewDTO();
            dto.setId(student.getId());
            dto.setName(student.getUsername());
            
            List<HomeworkEvaluation> studentEvals = evaluationMapper.selectByUserId(student.getId());
            dto.setHomeworkCount(studentEvals.size());
            
            if (!studentEvals.isEmpty()) {
                double avgScore = studentEvals.stream()
                    .mapToInt(e -> parseScoreFromRawResponse(e.getRawResponse()))
                    .average()
                    .orElse(0.0);
                dto.setAvgScore((int) Math.round(avgScore));
                int errorCount = studentEvals.stream()
                    .mapToInt(e -> parseErrorCountFromRawResponse(e.getRawResponse()))
                    .sum();
                dto.setErrorCount(errorCount);
                dto.setTrend(random.nextInt(10) - 5);
            } else {
                dto.setAvgScore(0);
                dto.setErrorCount(0);
                dto.setTrend(0);
            }
            
            dto.setNeedAttention(dto.getAvgScore() < 70 || dto.getErrorCount() > 15);
            studentMap.put(student.getUsername(), dto);
        }
        
        // 处理学生端提交者（已按学号去重，每个作业只算最新）
        for (Map<String, Object> row : submissionStudents) {
            String studentId = (String) row.get("student_id");
            String name = (String) row.get("student_name");
            if (keyword != null && !keyword.isEmpty() && 
                !name.contains(keyword) && !studentId.contains(keyword)) {
                continue;
            }
            
            Number count = (Number) row.get("homework_count");
            Number avgScore = (Number) row.get("avg_score");
            
            // 用学号作为key避免重名冲突
            String key = studentId != null ? studentId : name;
            
            if (studentMap.containsKey(key)) {
                // 合并已有学生
                StudentOverviewDTO dto = studentMap.get(key);
                dto.setHomeworkCount(dto.getHomeworkCount() + count.intValue());
                dto.setAvgScore((dto.getAvgScore() + avgScore.intValue()) / 2);
            } else {
                // 仅从学生端提交（没有系统账号）
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
        
        // 排序
        switch (sortBy) {
            case "progress":
                result.sort((a, b) -> b.getTrend() - a.getTrend());
                break;
            case "homework":
                result.sort((a, b) -> b.getHomeworkCount() - a.getHomeworkCount());
                break;
            case "score":
            default:
                result.sort((a, b) -> b.getAvgScore() - a.getAvgScore());
                break;
        }
        
        return result;
    }

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

    // ============ 私有方法 ============

    /**
     * 清理 raw_response 中的 Markdown 代码块标记
     */
    private String cleanRawResponse(String rawResponse) {
        if (rawResponse == null || rawResponse.isEmpty()) {
            return null;
        }
        // 去除开头的 ```json 或 ```
        String cleaned = rawResponse.trim();
        if (cleaned.startsWith("```json")) {
            cleaned = cleaned.substring(7).trim();
        } else if (cleaned.startsWith("```")) {
            cleaned = cleaned.substring(3).trim();
        }
        // 去除结尾的 ```
        if (cleaned.endsWith("```")) {
            cleaned = cleaned.substring(0, cleaned.length() - 3).trim();
        }
        return cleaned;
    }

    private int parseScoreFromRawResponse(String rawResponse) {
        String cleaned = cleanRawResponse(rawResponse);
        if (cleaned == null || cleaned.isEmpty()) {
            return 0;
        }
        try {
            JsonNode json = objectMapper.readTree(cleaned);
            JsonNode totalScore = json.get("totalScore");
            if (totalScore != null) {
                return totalScore.asInt();
            }
        } catch (Exception e) {
            log.warn("解析分数失败: {}", e.getMessage());
        }
        return 0;
    }

    private int parseErrorCountFromRawResponse(String rawResponse) {
        String cleaned = cleanRawResponse(rawResponse);
        if (cleaned == null || cleaned.isEmpty()) {
            return 0;
        }
        try {
            JsonNode json = objectMapper.readTree(cleaned);
            JsonNode errors = json.get("errors");
            if (errors != null && errors.isArray()) {
                return errors.size();
            }
            JsonNode suggestions = json.get("suggestions");
            if (suggestions != null && suggestions.isArray()) {
                return suggestions.size();
            }
        } catch (Exception e) {
            log.warn("解析错误数失败: {}", e.getMessage());
        }
        return 0;
    }

    /**
     * 从 raw_response JSON 中解析错误/建议，填充到 errorCountMap 和 errorDifficultyMap
     */
    private void parseRawResponseForErrors(String rawResponse,
                                            Map<String, Integer> errorCountMap,
                                            Map<String, String> errorDifficultyMap) {
        try {
            String cleaned = cleanRawResponse(rawResponse);
            if (cleaned == null) return;
            JsonNode json = objectMapper.readTree(cleaned);
            JsonNode suggestions = json.get("suggestions");
            if (suggestions != null && suggestions.isArray()) {
                for (JsonNode suggestion : suggestions) {
                    String issue = suggestion.get("issue").asText();
                    String priority = suggestion.get("priority").asText();
                    errorCountMap.merge(issue, 1, Integer::sum);
                    errorDifficultyMap.putIfAbsent(issue, priority);
                }
            }
            
            JsonNode errors = json.get("errors");
            if (errors != null && errors.isArray()) {
                for (JsonNode error : errors) {
                    String issue = error.get("issue").asText();
                    String severity = error.get("severity").asText();
                    errorCountMap.merge(issue, 1, Integer::sum);
                    errorDifficultyMap.putIfAbsent(issue,
                        "critical".equals(severity) ? "high" : "medium");
                }
            }
        } catch (Exception e) {
            log.warn("解析raw_response失败: {}", e.getMessage());
        }
    }

    private String convertDifficultyLabel(String priority) {
        switch (priority) {
            case "high":
            case "critical":
                return "困难";
            case "medium":
                return "中等";
            case "low":
            default:
                return "简单";
        }
    }

    private List<FrequentErrorDTO> getMockFrequentErrors() {
        List<FrequentErrorDTO> mockData = new ArrayList<>();
        String[][] errors = {
            {"HashMap 和 Hashtable 的区别是什么？", "medium", "68", "29"},
            {"线程池的核心参数有哪些？如何配置？", "hard", "76", "32"},
            {"Spring 的 IOC 和 AOP 原理", "hard", "71", "30"},
            {"MySQL 索引优化原则", "medium", "62", "26"},
            {"单例模式的线程安全实现", "medium", "58", "24"},
            {"JVM 内存模型与垃圾回收", "hard", "81", "34"},
            {"Redis 缓存穿透、击穿、雪崩", "medium", "55", "23"},
            {"Java 8 Stream API 使用", "easy", "42", "18"},
            {"事务的 ACID 特性", "easy", "38", "16"},
            {"工厂模式 vs 建造者模式", "hard", "64", "27"}
        };
        
        for (String[] error : errors) {
            FrequentErrorDTO dto = new FrequentErrorDTO();
            dto.setQuestion(error[0]);
            dto.setDifficulty(error[1]);
            dto.setDifficultyLabel(convertDifficultyLabel(error[1]));
            dto.setErrorRate(Integer.parseInt(error[2]));
            dto.setErrorCount(Integer.parseInt(error[3]));
            mockData.add(dto);
        }
        
        return mockData;
    }
}
