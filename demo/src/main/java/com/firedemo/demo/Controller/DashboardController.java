package com.firedemo.demo.Controller;

import com.firedemo.demo.DTO.*;
import com.firedemo.demo.Service.DashboardService;
import com.firedemo.demo.config.Result;
import com.firedemo.demo.utils.JwtUtil;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;


import java.util.List;

/**
 * 仪表盘数据控制器
 */
@RestController
@RequestMapping("/api/dashboard")
@RequiredArgsConstructor
public class DashboardController {

    private final DashboardService dashboardService;
    private final JwtUtil jwtUtil;

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
}
