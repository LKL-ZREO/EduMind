package com.firedemo.demo.Service.ServiceImpl;

import com.firedemo.demo.DTO.ClassInfoDTO;
import com.firedemo.demo.DTO.DashboardMetricsDTO;
import com.firedemo.demo.DTO.FrequentErrorDTO;
import com.firedemo.demo.DTO.KnowledgeMasteryDTO;
import com.firedemo.demo.DTO.ScoreDistributionDTO;
import com.firedemo.demo.DTO.StudentInsightDTO;
import com.firedemo.demo.DTO.StudentOverviewDTO;
import com.firedemo.demo.DTO.TeacherKnowledgeDTO;
import com.firedemo.demo.Entity.Submission;
import com.firedemo.demo.Entity.TeacherKnowledge;
import com.firedemo.demo.Entity.User;
import com.firedemo.demo.Service.DashboardService;
import com.firedemo.demo.Service.KnowledgePointVocabularyService;
import com.firedemo.demo.common.exception.BusinessException;
import com.firedemo.demo.common.exception.ErrorCode;
import com.firedemo.demo.mapper.ClassInfoMapper;
import com.firedemo.demo.mapper.LegacyHomeworkEvaluationStatsMapper;
import com.firedemo.demo.mapper.SubmissionErrorMapper;
import com.firedemo.demo.mapper.SubmissionMapper;
import com.firedemo.demo.mapper.TeacherKnowledgeMapper;
import com.firedemo.demo.mapper.UserMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.CacheConfig;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cache.annotation.Caching;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Slf4j
@Service
@RequiredArgsConstructor
@CacheConfig(cacheNames = "dashboard")
public class DashboardServiceImpl implements DashboardService {

    private final LegacyHomeworkEvaluationStatsMapper legacyEvaluationStatsMapper;
    private final UserMapper userMapper;
    private final ClassInfoMapper classInfoMapper;
    private final SubmissionMapper submissionMapper;
    private final TeacherKnowledgeMapper teacherKnowledgeMapper;
    private final SubmissionErrorMapper submissionErrorMapper;
    private final CacheManager cacheManager;

    // ======================== Core Metrics ========================

    @Override
    @Cacheable(key = "'metrics:' + #classId", sync = true)
    public DashboardMetricsDTO getMetrics(Long classId) {
        DashboardMetricsDTO metrics = new DashboardMetricsDTO();
        Integer studentCount = submissionMapper.countDistinctStudentsByClassId(classId);
        metrics.setTotalStudents(studentCount != null ? studentCount : 0);
        metrics.setStudentTrend(0);

        Integer totalHomework = legacyEvaluationStatsMapper.countByClassId(classId);
        Integer submissionCount = submissionMapper.countByClassId(classId);
        metrics.setTotalHomework((totalHomework != null ? totalHomework : 0) + (submissionCount != null ? submissionCount : 0));

        LocalDateTime weekAgo = LocalDateTime.now().minusDays(7);
        Integer newHomework = legacyEvaluationStatsMapper.countNewByClassId(classId, weekAgo);
        Integer newSubmission = submissionMapper.countNewByClassId(classId, weekAgo);
        metrics.setNewHomework((newHomework != null ? newHomework : 0) + (newSubmission != null ? newSubmission : 0));

        List<Integer> evalScores = legacyEvaluationStatsMapper.selectScoresByClassId(classId);
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
    @Cacheable(key = "'errors:' + #classId", sync = true)
    public List<FrequentErrorDTO> getFrequentErrors(Long classId, String knowledgePoint) {
        int totalStudents = Math.max(0,
                valueOrZero(submissionMapper.countDistinctStudentsByClassId(classId)));
        return submissionErrorMapper.selectFrequentErrorStats(classId, knowledgePoint, 20).stream()
                .map(row -> {
                    FrequentErrorDTO dto = new FrequentErrorDTO();
                    dto.setQuestion((String) row.get("question"));
                    dto.setKnowledgePoint((String) row.get("knowledge_point"));
                    int severityRank = intValue(row.get("severity_rank"));
                    String difficulty = severityRank >= 3 ? "high" : severityRank == 2 ? "medium" : "low";
                    dto.setDifficulty(difficulty);
                    dto.setDifficultyLabel(convertDifficultyLabel(difficulty));
                    dto.setErrorCount(intValue(row.get("error_count")));
                    int affectedStudents = intValue(row.get("affected_student_count"));
                    dto.setAffectedStudentCount(affectedStudents);
                    double affectedRate = totalStudents > 0
                            ? Math.round(affectedStudents * 1000.0 / totalStudents) / 10.0 : 0.0;
                    dto.setAffectedStudentRate(affectedRate);
                    dto.setErrorRate((int) Math.round(affectedRate));
                    dto.setAssignmentCount(intValue(row.get("assignment_count")));
                    dto.setLatestSeenAt(localDateTimeValue(row.get("latest_seen_at")));
                    return dto;
                })
                .toList();
    }

    // ======================== Student Overview ========================

    @Override
    @Cacheable(key = "'students:' + #classId", sync = true)
    public List<StudentOverviewDTO> getStudentOverview(Long classId, String sortBy, String keyword) {
        List<User> students = userMapper.selectStudentsByClassId(classId);
        List<Map<String, Object>> submissionStudents = submissionMapper.selectStudentOverviewByClassId(classId);
        Map<String, StudentOverviewDTO> studentMap = new LinkedHashMap<>();

        List<Map<String, Object>> teacherStats = legacyEvaluationStatsMapper.selectStudentStatsByClassId(classId);
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

    @Override
    public StudentInsightDTO getStudentInsight(Long classId, String studentId, String studentName) {
        String normalizedStudentId = trimToNull(studentId);
        String normalizedStudentName = trimToNull(studentName);
        if (normalizedStudentId == null && normalizedStudentName == null) {
            throw new BusinessException(ErrorCode.PARAM_ERROR.getCode(), "学生学号或姓名至少填写一项");
        }

        List<Submission> rawSubmissions = normalizedStudentId != null
                ? submissionMapper.selectByStudentIdAndClassOrderByNo(normalizedStudentId, classId)
                : submissionMapper.selectByStudentAndClassOrderByNo(normalizedStudentName, classId);
        List<Submission> submissions = rawSubmissions.stream()
                .filter(submission -> submission.getTotalScore() != null)
                .toList();
        if (submissions.isEmpty()) {
            throw new BusinessException(ErrorCode.DATA_NOT_FOUND.getCode(), "该学生暂无有效作业记录");
        }

        String resolvedStudentId = normalizedStudentId != null
                ? normalizedStudentId : submissions.get(0).getStudentId();
        String resolvedStudentName = normalizedStudentName != null
                ? normalizedStudentName : submissions.get(0).getStudentName();

        List<StudentInsightDTO.ScorePoint> scoreHistory = new ArrayList<>();
        int previousScore = 0;
        for (int index = 0; index < submissions.size(); index++) {
            Submission submission = submissions.get(index);
            int score = submission.getTotalScore();
            scoreHistory.add(StudentInsightDTO.ScorePoint.builder()
                    .no(index + 1)
                    .submissionId(submission.getId())
                    .assignmentName(submission.getAssignmentName())
                    .date(submission.getSubmittedAt() == null
                            ? "" : submission.getSubmittedAt().toLocalDate().toString())
                    .score(score)
                    .change(index == 0 ? 0 : score - previousScore)
                    .late(Boolean.TRUE.equals(submission.getIsLate()))
                    .build());
            previousScore = score;
        }

        int average = (int) Math.round(submissions.stream()
                .mapToInt(Submission::getTotalScore).average().orElse(0));
        int latest = submissions.get(submissions.size() - 1).getTotalScore();
        int highest = submissions.stream().mapToInt(Submission::getTotalScore).max().orElse(0);
        int lowest = submissions.stream().mapToInt(Submission::getTotalScore).min().orElse(0);
        int latestChange = scoreHistory.get(scoreHistory.size() - 1).getChange();
        int lateCount = (int) submissions.stream().filter(item -> Boolean.TRUE.equals(item.getIsLate())).count();

        List<StudentInsightDTO.WeakKnowledgePoint> weakPoints = submissionErrorMapper
                .selectStudentKnowledgeStats(classId, resolvedStudentId, resolvedStudentName, 8).stream()
                .map(row -> StudentInsightDTO.WeakKnowledgePoint.builder()
                        .name((String) row.get("name"))
                        .errorCount(intValue(row.get("error_count")))
                        .criticalCount(intValue(row.get("critical_count")))
                        .latestSeenAt(localDateTimeValue(row.get("latest_seen_at")))
                        .build())
                .toList();

        List<StudentInsightDTO.RecentError> recentErrors = submissionErrorMapper
                .selectRecentStudentErrors(classId, resolvedStudentId, resolvedStudentName, 12).stream()
                .map(row -> StudentInsightDTO.RecentError.builder()
                        .id(longValue(row.get("id")))
                        .submissionId(longValue(row.get("submission_id")))
                        .assignmentName((String) row.get("assignment_name"))
                        .knowledgePoint((String) row.get("knowledge_point"))
                        .errorText((String) row.get("error_text"))
                        .severity((String) row.get("severity"))
                        .createdAt(localDateTimeValue(row.get("created_at")))
                        .build())
                .toList();

        int totalErrors = weakPoints.stream()
                .mapToInt(StudentInsightDTO.WeakKnowledgePoint::getErrorCount).sum();
        int criticalErrors = weakPoints.stream()
                .mapToInt(StudentInsightDTO.WeakKnowledgePoint::getCriticalCount).sum();
        StudentInsightDTO.Risk risk = buildStudentRisk(
                average, latest, latestChange, lateCount, submissions, weakPoints);

        return StudentInsightDTO.builder()
                .student(StudentInsightDTO.StudentIdentity.builder()
                        .studentId(resolvedStudentId)
                        .name(resolvedStudentName)
                        .build())
                .summary(StudentInsightDTO.Summary.builder()
                        .avgScore(average)
                        .latestScore(latest)
                        .highestScore(highest)
                        .lowestScore(lowest)
                        .completedCount(submissions.size())
                        .lateCount(lateCount)
                        .totalErrorCount(totalErrors)
                        .criticalErrorCount(criticalErrors)
                        .latestChange(latestChange)
                        .build())
                .risk(risk)
                .scoreHistory(scoreHistory)
                .weakKnowledgePoints(weakPoints)
                .recentErrors(recentErrors)
                .build();
    }

    // ======================== Class List ========================

    @Override
    @Cacheable(key = "'classList:' + #teacherId", sync = true)
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
    @Transactional(rollbackFor = Exception.class)
    @Caching(evict = {
        @CacheEvict(key = "'knowledge:' + #classId"),
        @CacheEvict(key = "'metrics:' + #classId"),
        @CacheEvict(key = "'scoreDist:' + #classId"),
        @CacheEvict(key = "'errors:' + #classId"),
        @CacheEvict(key = "'students:' + #classId")
    })
    public void saveTeacherKnowledge(Long classId, Long userId, List<TeacherKnowledgeDTO> items) {
        if (items == null || items.isEmpty()) {
            throw new BusinessException(ErrorCode.PARAM_ERROR.getCode(), "知识点列表不能为空");
        }
        ensureOtherExists(classId, userId);
        List<TeacherKnowledge> existing = teacherKnowledgeMapper.selectByClassId(classId);
        Map<Long, TeacherKnowledge> existingById = existing.stream()
                .collect(java.util.stream.Collectors.toMap(TeacherKnowledge::getId, item -> item));
        Map<String, TeacherKnowledge> existingByName = existing.stream()
                .collect(java.util.stream.Collectors.toMap(TeacherKnowledge::getName, item -> item));

        List<KnowledgeSyncItem> requested = new ArrayList<>();
        Set<String> requestedNames = new java.util.LinkedHashSet<>();
        Set<Long> requestedIds = new java.util.HashSet<>();
        Set<Long> retainedIds = new java.util.HashSet<>();
        int fallbackOrder = 0;
        for (TeacherKnowledgeDTO dto : items) {
            if (dto == null) {
                throw new BusinessException(ErrorCode.PARAM_ERROR.getCode(), "知识点列表包含空项");
            }
            if (dto.getId() != null && !requestedIds.add(dto.getId())) {
                throw new BusinessException(ErrorCode.PARAM_ERROR.getCode(), "知识点ID不能重复");
            }
            String name = requireKnowledgeName(dto.getName());
            if (KnowledgePointVocabularyService.OTHER.equals(name)) {
                if (dto.getId() != null) {
                    TeacherKnowledge item = existingById.get(dto.getId());
                    if (item != null && !KnowledgePointVocabularyService.OTHER.equals(item.getName())) {
                        throw new BusinessException(ErrorCode.PARAM_ERROR.getCode(), "不能将普通知识点改名为‘其他’");
                    }
                }
                continue;
            }
            if (!requestedNames.add(name)) {
                throw new BusinessException(ErrorCode.DATA_ALREADY_EXISTS.getCode(), "知识点名称不能重复：" + name);
            }

            TeacherKnowledge matched = null;
            if (dto.getId() != null) {
                matched = existingById.get(dto.getId());
                if (matched == null || !classId.equals(matched.getClassId())) {
                    throw new BusinessException(ErrorCode.DATA_NOT_FOUND.getCode(), "知识点不存在或不属于当前班级");
                }
                if (KnowledgePointVocabularyService.OTHER.equals(matched.getName())) {
                    throw new BusinessException(ErrorCode.PARAM_ERROR.getCode(), "系统兜底知识点‘其他’不可修改");
                }
            } else if (existingByName.containsKey(name)
                    && !KnowledgePointVocabularyService.OTHER.equals(name)) {
                matched = existingByName.get(name);
            }
            if (matched != null) retainedIds.add(matched.getId());
            int sortOrder = dto.getSortOrder() == null ? fallbackOrder : dto.getSortOrder();
            requested.add(new KnowledgeSyncItem(matched, name, normalizeColor(dto.getColor()), sortOrder));
            fallbackOrder++;
        }

        List<KnowledgeRename> renames = new ArrayList<>();
        for (KnowledgeSyncItem item : requested) {
            TeacherKnowledge current = item.existing();
            if (current == null || current.getName().equals(item.name())) continue;
            String temporaryName = "__kp_tmp_" + current.getId() + "_"
                    + java.util.UUID.randomUUID().toString().substring(0, 8);
            submissionErrorMapper.updateKnowledgePoint(classId, current.getName(), temporaryName);
            renames.add(new KnowledgeRename(current, temporaryName, item.name()));
            current.setName(temporaryName);
            current.setUpdatedAt(LocalDateTime.now());
            teacherKnowledgeMapper.updateById(current);
        }

        for (TeacherKnowledge current : existing) {
            if (KnowledgePointVocabularyService.OTHER.equals(current.getName())) continue;
            if (retainedIds.contains(current.getId())) continue;
            String originalName = renames.stream()
                    .filter(rename -> rename.knowledge().getId().equals(current.getId()))
                    .map(KnowledgeRename::temporaryName)
                    .findFirst()
                    .orElse(current.getName());
            submissionErrorMapper.updateKnowledgePoint(
                    classId, originalName, KnowledgePointVocabularyService.OTHER);
            teacherKnowledgeMapper.deleteById(current.getId());
        }

        Map<Long, KnowledgeSyncItem> syncByExistingId = requested.stream()
                .filter(item -> item.existing() != null)
                .collect(java.util.stream.Collectors.toMap(item -> item.existing().getId(), item -> item));
        for (KnowledgeRename rename : renames) {
            KnowledgeSyncItem item = syncByExistingId.get(rename.knowledge().getId());
            submissionErrorMapper.updateKnowledgePoint(classId, rename.temporaryName(), rename.finalName());
            TeacherKnowledge knowledge = rename.knowledge();
            knowledge.setName(rename.finalName());
            knowledge.setColor(item.color());
            knowledge.setSortOrder(item.sortOrder());
            knowledge.setUpdatedAt(LocalDateTime.now());
            teacherKnowledgeMapper.updateById(knowledge);
        }

        for (KnowledgeSyncItem item : requested) {
            if (item.existing() != null) {
                if (item.existing().getName().startsWith("__kp_tmp_")) continue;
                item.existing().setColor(item.color());
                item.existing().setSortOrder(item.sortOrder());
                item.existing().setUpdatedAt(LocalDateTime.now());
                teacherKnowledgeMapper.updateById(item.existing());
                continue;
            }
            TeacherKnowledge knowledge = new TeacherKnowledge();
            knowledge.setClassId(classId);
            knowledge.setName(item.name());
            knowledge.setColor(item.color());
            knowledge.setSortOrder(item.sortOrder());
            knowledge.setCreatedBy(userId);
            teacherKnowledgeMapper.insert(knowledge);
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    @Caching(evict = {
        @CacheEvict(key = "'knowledge:' + #classId"),
        @CacheEvict(key = "'metrics:' + #classId"),
        @CacheEvict(key = "'scoreDist:' + #classId"),
        @CacheEvict(key = "'errors:' + #classId"),
        @CacheEvict(key = "'students:' + #classId")
    })
    public void addTeacherKnowledge(Long classId, Long userId, String name, String color) {
        String normalizedName = requireKnowledgeName(name);
        if (KnowledgePointVocabularyService.OTHER.equals(normalizedName)) {
            throw new BusinessException(ErrorCode.PARAM_ERROR.getCode(), "‘其他’是系统兜底知识点，无需手动添加");
        }
        if (teacherKnowledgeMapper.exists(classId, normalizedName)) {
            throw new BusinessException(ErrorCode.DATA_ALREADY_EXISTS.getCode(), "知识点已存在：" + normalizedName);
        }
        TeacherKnowledge tk = new TeacherKnowledge();
        tk.setClassId(classId);
        tk.setName(normalizedName);
        tk.setColor(normalizeColor(color));
        tk.setSortOrder(0);
        tk.setCreatedBy(userId);
        teacherKnowledgeMapper.insert(tk);
        ensureOtherExists(classId, userId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long deleteTeacherKnowledge(Long id) {
        TeacherKnowledge tk = teacherKnowledgeMapper.selectById(id);
        if (tk == null) {
            throw new BusinessException(ErrorCode.DATA_NOT_FOUND);
        }
        if (KnowledgePointVocabularyService.OTHER.equals(tk.getName())) {
            throw new BusinessException(ErrorCode.PARAM_ERROR.getCode(), "系统兜底知识点‘其他’不可删除");
        }
        submissionErrorMapper.updateKnowledgePoint(
                tk.getClassId(), tk.getName(), KnowledgePointVocabularyService.OTHER);
        teacherKnowledgeMapper.deleteById(id);
        evictDashboardCache(tk.getClassId());
        return tk.getClassId();
    }

    // ======================== Ensure "Other" ========================

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
        List<Map<String, Object>> evalStats = legacyEvaluationStatsMapper.selectStudentStatsByClassId(classId);
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

    private StudentInsightDTO.Risk buildStudentRisk(
            int average,
            int latest,
            int latestChange,
            int lateCount,
            List<Submission> submissions,
            List<StudentInsightDTO.WeakKnowledgePoint> weakPoints) {
        List<String> reasons = new ArrayList<>();
        List<String> suggestions = new ArrayList<>();
        boolean highRisk = false;

        if (average < 60) {
            reasons.add("累计平均成绩低于60分");
            suggestions.add("优先安排基础知识补救，并检查最近作业中的共性错误");
            highRisk = true;
        } else if (average < 70) {
            reasons.add("累计平均成绩处于60至69分区间");
            suggestions.add("安排一组基础巩固练习，确认核心概念掌握情况");
        }
        if (latest < 60) {
            reasons.add("最近一次作业未达到及格线");
            suggestions.add("回看最近一次作业，优先处理影响得分最大的错误");
            highRisk = true;
        }
        boolean consecutiveDecline = submissions.size() >= 3
                && submissions.get(submissions.size() - 3).getTotalScore()
                    > submissions.get(submissions.size() - 2).getTotalScore()
                && submissions.get(submissions.size() - 2).getTotalScore() > latest;
        if (consecutiveDecline) {
            reasons.add("最近两次成绩连续下降");
            suggestions.add("对比最近三次作业，确认下降是否集中在同一知识点");
            highRisk = true;
        } else if (latestChange <= -10) {
            reasons.add("最近一次成绩下降10分以上");
            suggestions.add("确认本次题目难度变化，并查看新增错误类型");
        }
        if (lateCount > 0) {
            reasons.add("存在" + lateCount + "次迟交记录");
            suggestions.add("了解迟交原因，并确认后续任务完成节奏");
        }
        weakPoints.stream()
                .filter(point -> !KnowledgePointVocabularyService.OTHER.equals(point.getName()))
                .findFirst()
                .filter(point -> point.getErrorCount() >= 3)
                .ifPresent(point -> {
                    reasons.add(point.getName() + "累计出现" + point.getErrorCount() + "条错误");
                    suggestions.add("针对“" + point.getName() + "”安排讲解或分层练习");
                });

        if (reasons.isEmpty()) reasons.add("当前未发现明确风险信号");
        if (suggestions.isEmpty()) suggestions.add("保持当前学习节奏，并继续观察下一次作业表现");
        if (submissions.size() < 3) suggestions.add("当前样本较少，建议积累至少3次作业后判断长期趋势");

        String level = highRisk ? "HIGH"
                : reasons.size() > 1 || average < 70 || latestChange < 0 || lateCount > 0 ? "MEDIUM" : "LOW";
        return StudentInsightDTO.Risk.builder()
                .level(level)
                .reasons(reasons.stream().distinct().toList())
                .suggestions(suggestions.stream().distinct().toList())
                .build();
    }

    // ======================== Utils ========================

    private String requireKnowledgeName(String value) {
        String name = trimToNull(value);
        if (name == null) {
            throw new BusinessException(ErrorCode.PARAM_ERROR.getCode(), "知识点名称不能为空");
        }
        if (name.length() > 100) {
            throw new BusinessException(ErrorCode.PARAM_ERROR.getCode(), "知识点名称不能超过100个字符");
        }
        return name;
    }

    private String normalizeColor(String color) {
        String normalized = trimToNull(color);
        return normalized != null && normalized.matches("#[0-9a-fA-F]{6}")
                ? normalized : "#1890ff";
    }

    private String trimToNull(String value) {
        if (value == null) return null;
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private int valueOrZero(Integer value) {
        return value == null ? 0 : value;
    }

    private int intValue(Object value) {
        return value instanceof Number number ? number.intValue() : 0;
    }

    private Long longValue(Object value) {
        return value instanceof Number number ? number.longValue() : null;
    }

    private LocalDateTime localDateTimeValue(Object value) {
        if (value instanceof LocalDateTime localDateTime) return localDateTime;
        if (value instanceof java.sql.Timestamp timestamp) return timestamp.toLocalDateTime();
        return null;
    }

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
            cache.evict("errors:" + classId);
            cache.evict("students:" + classId);
        }
    }

    private String convertDifficultyLabel(String priority) {
        switch (priority) {
            case "high":
            case "critical": return "高严重度";
            case "medium": return "中等";
            default: return "一般";
        }
    }

    private record KnowledgeSyncItem(
            TeacherKnowledge existing, String name, String color, int sortOrder) {}

    private record KnowledgeRename(
            TeacherKnowledge knowledge, String temporaryName, String finalName) {}
}
