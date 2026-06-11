package com.firedemo.demo.Controller;

import com.firedemo.demo.DTO.*;
import com.firedemo.demo.Entity.Submission;
import com.firedemo.demo.Entity.TeacherKnowledge;
import com.firedemo.demo.Service.DashboardService;
import com.firedemo.demo.Service.HomeworkResultService;
import com.firedemo.demo.Service.SubmissionService;
import com.firedemo.demo.common.exception.ErrorCode;
import com.firedemo.demo.common.prompt.PromptLoader;
import com.firedemo.demo.common.result.Result;
import com.firedemo.demo.utils.JwtUtil;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RBloomFilter;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.ArrayList;
import java.util.Map;

/**
 * 仪表盘数据控制器
 */
@Slf4j
@RestController
@RequestMapping("/api/dashboard")
@RequiredArgsConstructor
public class DashboardController {

    private final DashboardService dashboardService;
    private final JwtUtil jwtUtil;
    private final RBloomFilter<String> classIdBloomFilter;
    private final SubmissionService submissionService;
    private final HomeworkResultService homeworkResultService;
    private final PromptLoader promptLoader;

    // ======================== 核心数据 ========================

    @GetMapping("/metrics")
    public Result<DashboardMetricsDTO> getMetrics(
            @RequestParam Long classId, HttpServletRequest request) {
        Long userId = jwtUtil.getUserIdFromRequest(request);
        if (userId == null) return Result.error(401, "未登录");
        if (!classIdBloomFilter.contains(String.valueOf(classId)))
            return Result.error(ErrorCode.DATA_NOT_FOUND.getCode(), "班级不存在");
        return Result.success(dashboardService.getMetrics(classId));
    }

    @GetMapping("/score-distribution")
    public Result<List<ScoreDistributionDTO>> getScoreDistribution(
            @RequestParam Long classId, HttpServletRequest request) {
        Long userId = jwtUtil.getUserIdFromRequest(request);
        if (userId == null) return Result.error(401, "未登录");
        if (!classIdBloomFilter.contains(String.valueOf(classId)))
            return Result.error(ErrorCode.DATA_NOT_FOUND.getCode(), "班级不存在");
        return Result.success(dashboardService.getScoreDistribution(classId));
    }

    @GetMapping("/knowledge-mastery")
    public Result<List<KnowledgeMasteryDTO>> getKnowledgeMastery(
            @RequestParam Long classId, HttpServletRequest request) {
        Long userId = jwtUtil.getUserIdFromRequest(request);
        if (userId == null) return Result.error(401, "未登录");
        if (!classIdBloomFilter.contains(String.valueOf(classId)))
            return Result.error(ErrorCode.DATA_NOT_FOUND.getCode(), "班级不存在");
        return Result.success(dashboardService.getKnowledgeMastery(classId));
    }

    @GetMapping("/frequent-errors")
    public Result<List<FrequentErrorDTO>> getFrequentErrors(
            @RequestParam Long classId,
            @RequestParam(required = false) String knowledgePoint,
            HttpServletRequest request) {
        Long userId = jwtUtil.getUserIdFromRequest(request);
        if (userId == null) return Result.error(401, "未登录");
        if (!classIdBloomFilter.contains(String.valueOf(classId)))
            return Result.error(ErrorCode.DATA_NOT_FOUND.getCode(), "班级不存在");
        return Result.success(dashboardService.getFrequentErrors(classId, knowledgePoint));
    }

    @GetMapping("/students")
    public Result<List<StudentOverviewDTO>> getStudentOverview(
            @RequestParam Long classId,
            @RequestParam(required = false, defaultValue = "score") String sortBy,
            @RequestParam(required = false) String keyword,
            HttpServletRequest request) {
        Long userId = jwtUtil.getUserIdFromRequest(request);
        if (userId == null) return Result.error(401, "未登录");
        if (!classIdBloomFilter.contains(String.valueOf(classId)))
            return Result.error(ErrorCode.DATA_NOT_FOUND.getCode(), "班级不存在");
        return Result.success(dashboardService.getStudentOverview(classId, sortBy, keyword));
    }

    @GetMapping("/classes")
    public Result<List<ClassInfoDTO>> getClassList(HttpServletRequest request) {
        Long userId = jwtUtil.getUserIdFromRequest(request);
        if (userId == null) return Result.error(401, "未登录");
        return Result.success(dashboardService.getClassList(userId));
    }

    // ======================== 老师知识管理 ========================

    @GetMapping("/teacher-knowledge")
    public Result<List<TeacherKnowledge>> getTeacherKnowledge(
            @RequestParam Long classId, HttpServletRequest request) {
        Long userId = jwtUtil.getUserIdFromRequest(request);
        if (userId == null) return Result.error(401, "未登录");
        return Result.success(dashboardService.getTeacherKnowledge(classId));
    }

    @PostMapping("/teacher-knowledge/add")
    public Result<Void> addTeacherKnowledge(
            @RequestBody TeacherKnowledgeAddRequest body,
            HttpServletRequest request) {
        Long userId = jwtUtil.getUserIdFromRequest(request);
        if (userId == null) return Result.error(401, "未登录");
        if (!classIdBloomFilter.contains(String.valueOf(body.getClassId())))
            return Result.error(ErrorCode.DATA_NOT_FOUND.getCode(), "班级不存在");
        dashboardService.addTeacherKnowledge(body.getClassId(), userId, body.getName(), body.getColor());
        return Result.success(null);
    }

    @PostMapping("/teacher-knowledge/batch")
    public Result<Void> batchSaveTeacherKnowledge(
            @RequestBody TeacherKnowledgeSaveRequest body,
            HttpServletRequest request) {
        Long userId = jwtUtil.getUserIdFromRequest(request);
        if (userId == null) return Result.error(401, "未登录");
        if (!classIdBloomFilter.contains(String.valueOf(body.getClassId())))
            return Result.error(ErrorCode.DATA_NOT_FOUND.getCode(), "班级不存在");
        dashboardService.saveTeacherKnowledge(body.getClassId(), userId, body.getItems());
        return Result.success(null);
    }

    @DeleteMapping("/teacher-knowledge/{id}")
    public Result<Void> deleteTeacherKnowledge(
            @PathVariable Long id,
            HttpServletRequest request) {
        Long userId = jwtUtil.getUserIdFromRequest(request);
        if (userId == null) return Result.error(401, "未登录");
        dashboardService.deleteTeacherKnowledge(id);
        return Result.success(null);
    }

    // ======================== 学生成长曲线 ========================

    @GetMapping("/student-progress")
    public Result<Map<String, Object>> getStudentProgress(
            @RequestParam String studentName,
            @RequestParam Long classId,
            @RequestParam(required = false) String studentId,
            HttpServletRequest request) {
        Long userId = jwtUtil.getUserIdFromRequest(request);
        if (userId == null) return Result.error(401, "未登录");

        List<Submission> submissions;
        if (studentId != null && !studentId.isEmpty()) {
            submissions = submissionService.listByStudentIdAndClassOrderByNo(studentId, classId);
        } else {
            submissions = submissionService.listByStudentAndClassOrderByNo(studentName, classId);
        }
        if (submissions.isEmpty()) return Result.error(404, "暂无提交记录");

        List<Map<String, Object>> points = new ArrayList<>();
        for (int i = 0; i < submissions.size(); i++) {
            Submission s = submissions.get(i);
            Map<String, Object> p = new HashMap<>();
            p.put("no", i + 1);
            p.put("assignmentName", s.getAssignmentName());
            p.put("score", s.getTotalScore() != null ? s.getTotalScore() : 0);
            p.put("date", s.getSubmittedAt() != null ? s.getSubmittedAt().toLocalDate().toString() : "");
            p.put("change", i > 0 ? (s.getTotalScore() != null ? s.getTotalScore() : 0)
                    - (submissions.get(i - 1).getTotalScore() != null ? submissions.get(i - 1).getTotalScore() : 0) : 0);
            points.add(p);
        }

        double avgScore = points.stream().mapToInt(p -> (int) p.get("score")).average().orElse(0);
        int maxScore = points.stream().mapToInt(p -> (int) p.get("score")).max().orElse(0);
        int minScore = points.stream().mapToInt(p -> (int) p.get("score")).min().orElse(0);
        double trend = points.size() >= 2 ? Math.round(((int) points.get(points.size() - 1).get("score") - avgScore) * 10.0) / 10.0 : 0;

        Map<String, Object> result = new HashMap<>();
        result.put("studentName", studentName);
        result.put("totalCount", submissions.size());
        result.put("avgScore", Math.round(avgScore * 10.0) / 10.0);
        result.put("maxScore", maxScore);
        result.put("minScore", minScore);
        result.put("trend", trend);
        result.put("points", points);
        return Result.success(result);
    }

    // ======================== 教案生成 ========================

    @GetMapping("/weak-points")
    public Result<List<String>> getWeakKnowledgePoints(
            @RequestParam Long classId, HttpServletRequest request) {
        Long userId = jwtUtil.getUserIdFromRequest(request);
        if (userId == null) return Result.error(401, "未登录");
        // 改用 SubmissionErrorMapper 统计薄弱知识点
        List<Map<String, Object>> weakStats = dashboardService.getFrequentErrors(classId, null).stream()
                .filter(e -> e.getErrorCount() > 5)
                .map(e -> {
                    Map<String, Object> m = new HashMap<>();
                    m.put("name", e.getKnowledgePoint());
                    return m;
                })
                .collect(java.util.stream.Collectors.toList());
        List<String> weakPoints = weakStats.stream()
                .map(m -> (String) m.get("name"))
                .filter(n -> n != null && !"其他".equals(n))
                .distinct()
                .collect(java.util.stream.Collectors.toList());
        return Result.success(weakPoints);
    }

    @PostMapping("/teaching-plan/generate")
    public Result<String> generatePlan(
            @RequestBody TeachingPlanRequestDTO requestDTO,
            HttpServletRequest request) {
        Long userId = jwtUtil.getUserIdFromRequest(request);
        if (userId == null) return Result.error(401, "未登录");

        StringBuilder plan = new StringBuilder();
        String template = promptLoader.load("teaching-plan.txt");

        StringBuilder goalsHtml = new StringBuilder();
        for (String goal : requestDTO.getGoals()) {
            goalsHtml.append("  <li>");
            switch (goal) {
                case "basic": goalsHtml.append("巩固基础知识，建立扎实理论功底"); break;
                case "difficult": goalsHtml.append("突破重点难点，深入理解核心概念"); break;
                case "extend": goalsHtml.append("举一反三，培养知识迁移能力"); break;
                case "review": goalsHtml.append("查漏补缺，完善知识体系"); break;
                default: goalsHtml.append(goal);
            }
            goalsHtml.append("</li>\n");
        }

        String knowledgePoints = String.join("、", requestDTO.getWeakKnowledgePoints());
        String mainPoint = requestDTO.getWeakKnowledgePoints().get(0);
        String diffPoint = requestDTO.getWeakKnowledgePoints().size() > 1
                ? requestDTO.getWeakKnowledgePoints().get(1) : mainPoint;

        String result = template
                .replace("{{knowledgePoints}}", knowledgePoints)
                .replace("{{goals}}", goalsHtml.toString())
                .replace("{{mainPoint}}", mainPoint)
                .replace("{{difficultPoint}}", diffPoint);
        plan.append(result);
        return Result.success(plan.toString());
    }
}
