package com.firedemo.demo.Controller;

import com.firedemo.demo.DTO.*;
import com.firedemo.demo.DTO.TeachingPlanRequestDTO;
import com.firedemo.demo.Entity.Submission;
import com.firedemo.demo.Service.DashboardRagService;
import com.firedemo.demo.Service.DashboardService;
import com.firedemo.demo.Service.HomeworkResultService;
import com.firedemo.demo.Service.SubmissionService;
import com.firedemo.demo.common.exception.ErrorCode;
import com.firedemo.demo.common.result.Result;
import com.firedemo.demo.rag.VectorStoreService;
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
    private final DashboardRagService dashboardRagService;
    private final VectorStoreService vectorStoreService;
    private final RBloomFilter<String> classIdBloomFilter;
    private final SubmissionService submissionService;
    private final HomeworkResultService homeworkResultService;

    /**
     * 获取核心指标
     */
    @GetMapping("/metrics")
    public Result<DashboardMetricsDTO> getMetrics(
            @RequestParam Long classId,
            HttpServletRequest request) {
        // 验证教师权限
        Long userId = jwtUtil.getUserIdFromRequest(request);
        if (userId == null) {
            return Result.error(401, "未登录");
        }
        // 布隆过滤器防穿透 — 不存在的班级ID直接拦截
        if (!classIdBloomFilter.contains(String.valueOf(classId))) {
            return Result.error(ErrorCode.DATA_NOT_FOUND.getCode(), "班级不存在");
        }
        
        DashboardMetricsDTO metrics = dashboardService.getMetrics(classId);
        return Result.success(metrics);
    }

    /**
     * 获取成绩分布
     */
    @GetMapping("/score-distribution")
    public Result<List<ScoreDistributionDTO>> getScoreDistribution(
            @RequestParam Long classId,
            HttpServletRequest request) {
        Long userId = jwtUtil.getUserIdFromRequest(request);
        if (userId == null) {
            return Result.error(401, "未登录");
        }
        if (!classIdBloomFilter.contains(String.valueOf(classId))) {
            return Result.error(ErrorCode.DATA_NOT_FOUND.getCode(), "班级不存在");
        }
        
        List<ScoreDistributionDTO> distribution = dashboardService.getScoreDistribution(classId);
        return Result.success(distribution);
    }

    /**
     * 获取知识点掌握度
     */
    @GetMapping("/knowledge-mastery")
    public Result<List<KnowledgeMasteryDTO>> getKnowledgeMastery(
            @RequestParam Long classId,
            HttpServletRequest request) {
        Long userId = jwtUtil.getUserIdFromRequest(request);
        if (userId == null) {
            return Result.error(401, "未登录");
        }
        if (!classIdBloomFilter.contains(String.valueOf(classId))) {
            return Result.error(ErrorCode.DATA_NOT_FOUND.getCode(), "班级不存在");
        }
        
        List<KnowledgeMasteryDTO> mastery = dashboardService.getKnowledgeMastery(classId);
        return Result.success(mastery);
    }

    /**
     * 获取高频错题
     */
    @GetMapping("/frequent-errors")
    public Result<List<FrequentErrorDTO>> getFrequentErrors(
            @RequestParam Long classId,
            HttpServletRequest request) {
        Long userId = jwtUtil.getUserIdFromRequest(request);
        if (userId == null) {
            return Result.error(401, "未登录");
        }
        if (!classIdBloomFilter.contains(String.valueOf(classId))) {
            return Result.error(ErrorCode.DATA_NOT_FOUND.getCode(), "班级不存在");
        }
        
        List<FrequentErrorDTO> errors = dashboardService.getFrequentErrors(classId);
        return Result.success(errors);
    }

    /**
     * 获取学生概览列表
     */
    @GetMapping("/students")
    public Result<List<StudentOverviewDTO>> getStudentOverview(
            @RequestParam Long classId,
            @RequestParam(required = false, defaultValue = "score") String sortBy,
            @RequestParam(required = false) String keyword,
            HttpServletRequest request) {
        Long userId = jwtUtil.getUserIdFromRequest(request);
        if (userId == null) {
            return Result.error(401, "未登录");
        }
        if (!classIdBloomFilter.contains(String.valueOf(classId))) {
            return Result.error(ErrorCode.DATA_NOT_FOUND.getCode(), "班级不存在");
        }
        
        List<StudentOverviewDTO> students = dashboardService.getStudentOverview(classId, sortBy, keyword);
        return Result.success(students);
    }

    /**
     * 获取班级列表
     */
    @GetMapping("/classes")
    public Result<List<ClassInfoDTO>> getClassList(HttpServletRequest request) {
        Long userId = jwtUtil.getUserIdFromRequest(request);
        if (userId == null) {
            return Result.error(401, "未登录");
        }
        
        List<ClassInfoDTO> classes = dashboardService.getClassList(userId);
        return Result.success(classes);
    }
    
    /**
     * 上传仪表盘数据到RAG知识库
     */
    @PostMapping("/upload-to-rag")
    public Result uploadToRag(@RequestBody DashboardUploadDTO data, HttpServletRequest request) {
        Long userId = jwtUtil.getUserIdFromRequest(request);
        if (userId == null) {
            return Result.error(401, "未登录");
        }
        if (!classIdBloomFilter.contains(String.valueOf(data.getClassId()))) {
            return Result.error(ErrorCode.DATA_NOT_FOUND.getCode(), "班级不存在");
        }
        
        // 检查今天是否已上传
        String docIdPrefix = "dashboard_" + data.getClassId();
        if (vectorStoreService.existsToday(docIdPrefix)) {
            return Result.error(409, "今天已上传过该班级数据，请先删除旧数据再重新上传");
        }
        
        // 执行上传
        Map<String, Object> result = dashboardRagService.uploadDashboard(data);
        return Result.success(result);
    }
    
    /**
     * 检查今天是否已上传
     */
    @GetMapping("/check-rag-uploaded")
    public Result checkRagUploaded(@RequestParam Long classId, HttpServletRequest request) {
        Long userId = jwtUtil.getUserIdFromRequest(request);
        if (userId == null) {
            return Result.error(401, "未登录");
        }
        if (!classIdBloomFilter.contains(String.valueOf(classId))) {
            return Result.error(ErrorCode.DATA_NOT_FOUND.getCode(), "班级不存在");
        }
        
        String docIdPrefix = "dashboard_" + classId;
        boolean exists = vectorStoreService.existsToday(docIdPrefix);
        
        Map<String, Object> result = new HashMap<>();
        result.put("classId", classId);
        result.put("uploadedToday", exists);
        
        return Result.success(result);
    }

    // ========== 学生成长曲线（原 StudentProgressController） ==========

    @GetMapping("/student-progress")
    public Result<Map<String, Object>> getStudentProgress(
            @RequestParam String studentName,
            @RequestParam Long classId,
            @RequestParam(required = false) String studentId,
            HttpServletRequest request) {
        Long userId = jwtUtil.getUserIdFromRequest(request);
        if (userId == null) {
            return Result.error(401, "未登录");
        }

        List<Submission> submissions;
        if (studentId != null && !studentId.isEmpty()) {
            submissions = submissionService.listByStudentIdAndClassOrderByNo(studentId, classId);
        } else {
            submissions = submissionService.listByStudentAndClassOrderByNo(studentName, classId);
        }
        if (submissions.isEmpty()) {
            return Result.error(404, "暂无提交记录");
        }

        List<Map<String, Object>> points = new ArrayList<>();
        for (int i = 0; i < submissions.size(); i++) {
            Submission s = submissions.get(i);
            Map<String, Object> p = new HashMap<>();
            p.put("no", i + 1);
            p.put("assignmentName", s.getAssignmentName());
            p.put("score", s.getTotalScore() != null ? s.getTotalScore() : 0);
            p.put("date", s.getSubmittedAt() != null ? s.getSubmittedAt().toLocalDate().toString() : "");
            if (i > 0) {
                int prevScore = submissions.get(i - 1).getTotalScore() != null ?
                        submissions.get(i - 1).getTotalScore() : 0;
                p.put("change", (s.getTotalScore() != null ? s.getTotalScore() : 0) - prevScore);
            } else {
                p.put("change", 0);
            }
            points.add(p);
        }

        double avgScore = points.stream().mapToInt(p -> (int) p.get("score")).average().orElse(0);
        int maxScore = points.stream().mapToInt(p -> (int) p.get("score")).max().orElse(0);
        int minScore = points.stream().mapToInt(p -> (int) p.get("score")).min().orElse(0);

        double trend = 0;
        if (points.size() >= 2) {
            int lastScore = (int) points.get(points.size() - 1).get("score");
            trend = Math.round((lastScore - avgScore) * 10.0) / 10.0;
        }

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

    // ========== 教案生成（原 TeachingPlanController） ==========

    @GetMapping("/weak-points")
    public Result<List<String>> getWeakKnowledgePoints(
            @RequestParam Long classId,
            HttpServletRequest request) {
        Long userId = jwtUtil.getUserIdFromRequest(request);
        if (userId == null) {
            return Result.error(401, "未登录");
        }
        List<String> weakPoints = homeworkResultService.listWeakKnowledgePoints(classId);
        return Result.success(weakPoints);
    }

    @PostMapping("/teaching-plan/generate")
    public Result<String> generatePlan(
            @RequestBody TeachingPlanRequestDTO requestDTO,
            HttpServletRequest request) {
        Long userId = jwtUtil.getUserIdFromRequest(request);
        if (userId == null) {
            return Result.error(401, "未登录");
        }
        String plan = buildMockPlan(requestDTO);
        log.info("生成教案 - 班级ID: {}, 教师ID: {}", requestDTO.getClassId(), userId);
        return Result.success(plan);
    }

    private String buildMockPlan(TeachingPlanRequestDTO dto) {
        StringBuilder plan = new StringBuilder();
        plan.append("<h4>《");
        plan.append(String.join("、", dto.getWeakKnowledgePoints()));
        plan.append("》专项教案</h4>\n\n");
        plan.append("<p><strong>教学目标：</strong></p>\n<ul>\n");
        for (String goal : dto.getGoals()) {
            plan.append("  <li>");
            switch (goal) {
                case "basic": plan.append("巩固基础知识，建立扎实理论功底"); break;
                case "difficult": plan.append("突破重点难点，深入理解核心概念"); break;
                case "extend": plan.append("举一反三，培养知识迁移能力"); break;
                case "review": plan.append("查漏补缺，完善知识体系"); break;
                default: plan.append(goal);
            }
            plan.append("</li>\n");
        }
        plan.append("</ul>\n\n");
        plan.append("<p><strong>教学重点：</strong>");
        plan.append(dto.getWeakKnowledgePoints().get(0));
        plan.append("核心概念与原理</p>\n\n");
        plan.append("<p><strong>教学难点：</strong>");
        plan.append(dto.getWeakKnowledgePoints().get(1));
        plan.append("的实际应用与问题解决</p>\n\n");
        plan.append("<p><strong>教学方法：</strong></p>\n<ul>\n");
        plan.append("  <li>案例驱动：通过实际项目案例引入知识点</li>\n");
        plan.append("  <li>对比分析：对比易混淆概念的异同</li>\n");
        plan.append("  <li>实践练习：针对性编程练习巩固理解</li>\n");
        plan.append("</ul>\n\n");
        plan.append("<p><strong>建议课时：</strong>2课时（90分钟）</p>\n\n");
        plan.append("<p><strong>课后作业：</strong></p>\n<ul>\n");
        plan.append("  <li>完成课后练习题 5-10题</li>\n");
        plan.append("  <li>编写相关代码示例并提交</li>\n");
        plan.append("  <li>总结本节课知识点思维导图</li>\n");
        plan.append("</ul>");
        return plan.toString();
    }
}
