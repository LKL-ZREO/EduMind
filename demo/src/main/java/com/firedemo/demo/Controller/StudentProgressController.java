package com.firedemo.demo.Controller;

import com.firedemo.demo.common.result.Result;
import com.firedemo.demo.utils.JwtUtil;
import com.firedemo.demo.mapper.SubmissionMapper;
import com.firedemo.demo.Entity.Submission;
import jakarta.servlet.http.HttpServletRequest;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 学生个人成长曲线
 */
@RestController
@RequestMapping("/api/student")
@RequiredArgsConstructor
public class StudentProgressController {

    private final SubmissionMapper submissionMapper;
    private final JwtUtil jwtUtil;

    @GetMapping("/progress")
    public Result<Map<String, Object>> getStudentProgress(
            @RequestParam String studentName,
            @RequestParam Long classId,
            @RequestParam(required = false) String studentId,
            HttpServletRequest request) {

        Long userId = jwtUtil.getUserIdFromRequest(request);
        if (userId == null) {
            return Result.error(401, "未登录");
        }

        // 查询该学生提交记录，按作业序号排序
        List<Submission> submissions;
        if (studentId != null && !studentId.isEmpty()) {
            submissions = submissionMapper.selectByStudentIdAndClassOrderByNo(studentId, classId);
        } else {
            submissions = submissionMapper.selectByStudentAndClassOrderByNo(studentName, classId);
        }
        if (submissions.isEmpty()) {
            return Result.error(404, "暂无提交记录");
        }

        // 构建曲线数据
        List<Map<String, Object>> points = new ArrayList<>();
        for (int i = 0; i < submissions.size(); i++) {
            Submission s = submissions.get(i);
            Map<String, Object> p = new HashMap<>();
            p.put("no", i + 1);
            p.put("assignmentName", s.getAssignmentName());
            p.put("score", s.getTotalScore() != null ? s.getTotalScore() : 0);
            p.put("date", s.getSubmittedAt() != null ? s.getSubmittedAt().toLocalDate().toString() : "");

            // 环比变化
            if (i > 0) {
                int prevScore = submissions.get(i - 1).getTotalScore() != null ?
                        submissions.get(i - 1).getTotalScore() : 0;
                p.put("change", (s.getTotalScore() != null ? s.getTotalScore() : 0) - prevScore);
            } else {
                p.put("change", 0);
            }

            points.add(p);
        }

        // 统计
        double avgScore = points.stream().mapToInt(p -> (int) p.get("score")).average().orElse(0);
        int maxScore = points.stream().mapToInt(p -> (int) p.get("score")).max().orElse(0);
        int minScore = points.stream().mapToInt(p -> (int) p.get("score")).min().orElse(0);

        // 趋势：最新一次分数 - 平均分
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
}
