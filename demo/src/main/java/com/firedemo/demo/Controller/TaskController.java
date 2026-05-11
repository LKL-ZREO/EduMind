package com.firedemo.demo.Controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.firedemo.demo.Entity.HomeworkTask;
import com.firedemo.demo.Entity.Submission;
import com.firedemo.demo.config.Result;
import com.firedemo.demo.mapper.HomeworkTaskMapper;
import com.firedemo.demo.mapper.SubmissionMapper;
import com.firedemo.demo.utils.JwtUtil;
import jakarta.servlet.http.HttpServletRequest;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.*;

/**
 * 作业任务管理（教师端，需登录）
 */
@Slf4j
@RestController
@RequestMapping("/api/tasks")
@RequiredArgsConstructor
public class TaskController {

    private final HomeworkTaskMapper taskMapper;
    private final SubmissionMapper submissionMapper;
    private final com.firedemo.demo.mapper.ClassInfoMapper classInfoMapper;
    private final com.firedemo.demo.Service.TaskReminderService taskReminderService;
    private final com.firedemo.demo.Service.TaskReminderScheduleService taskReminderScheduleService;
    private final com.firedemo.demo.mapper.ClassStudentMapper classStudentMapper;
    private final JwtUtil jwtUtil;

    /**
     * 创建作业
     */
    @PostMapping
    public Result createTask(@RequestBody CreateTaskRequest req, HttpServletRequest request) {
        Long userId = jwtUtil.getUserIdFromRequest(request);

        HomeworkTask task = new HomeworkTask();
        task.setClassId(req.getClassId());
        task.setTaskName(req.getTaskName());
        task.setDescription(req.getDescription());
        task.setDeadline(req.getDeadline());
        task.setAllowLate(req.getAllowLate() != null ? req.getAllowLate() : true);
        task.setLatePenalty(req.getLatePenalty() != null ? req.getLatePenalty() : 0);
        task.setStatus("active");
        task.setCreatedBy(userId);
        task.setCreatedAt(LocalDateTime.now());
        task.setUpdatedAt(LocalDateTime.now());

        taskMapper.insert(task);
        log.info("创建作业: taskId={}, taskName={}, classId={}", task.getId(), req.getTaskName(), req.getClassId());

        // 发送作业发布通知
        taskReminderService.sendTaskPublishedNotification(
                req.getClassId(), req.getTaskName(), req.getDeadline());

        // 注册截止提醒定时任务
        taskReminderScheduleService.scheduleReminderTasks(task.getId());

        return Result.success(task);
    }

    /**
     * 编辑作业
     */
    @PutMapping("/{id}")
    public Result updateTask(@PathVariable Long id, @RequestBody CreateTaskRequest req) {
        HomeworkTask task = taskMapper.selectById(id);
        if (task == null) {
            return Result.error(404, "作业不存在");
        }

        task.setTaskName(req.getTaskName());
        task.setDescription(req.getDescription());
        task.setDeadline(req.getDeadline());
        task.setAllowLate(req.getAllowLate() != null ? req.getAllowLate() : task.getAllowLate());
        task.setLatePenalty(req.getLatePenalty() != null ? req.getLatePenalty() : task.getLatePenalty());
        task.setUpdatedAt(LocalDateTime.now());

        taskMapper.updateById(task);
        log.info("编辑作业: taskId={}", id);

        return Result.success(task);
    }

    /**
     * 删除作业
     */
    @DeleteMapping("/{id}")
    public Result deleteTask(@PathVariable Long id) {
        HomeworkTask task = taskMapper.selectById(id);
        if (task == null) {
            return Result.error(404, "作业不存在");
        }

        taskMapper.deleteById(id);
        log.info("删除作业: taskId={}", id);

        return Result.success(null);
    }

    /**
     * 获取作业列表（含统计信息）
     */
    @GetMapping
    public Result getTasks(@RequestParam Long classId) {
        List<HomeworkTask> tasks = taskMapper.selectByClassId(classId);

        // 为每个作业附加统计信息
        List<Map<String, Object>> result = new ArrayList<>();
        for (HomeworkTask task : tasks) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("id", task.getId());
            item.put("classId", task.getClassId());
            item.put("taskName", task.getTaskName());
            item.put("description", task.getDescription());
            item.put("deadline", task.getDeadline());
            item.put("allowLate", task.getAllowLate());
            item.put("latePenalty", task.getLatePenalty());
            item.put("status", task.getStatus());
            item.put("createdAt", task.getCreatedAt());

            // 统计：已提交人数、总提交次数、平均分（每个学生只取最新）
            List<Submission> submissions = submissionMapper.selectList(
                    new LambdaQueryWrapper<Submission>()
                            .eq(Submission::getTaskId, task.getId())
                            .orderByDesc(Submission::getSubmittedAt)
            );

            // 按学号去重，只保留最新提交
            Map<String, Submission> latestByStudent = new LinkedHashMap<>();
            for (Submission s : submissions) {
                String key = s.getStudentId() != null ? s.getStudentId() : s.getStudentName();
                if (!latestByStudent.containsKey(key)) {
                    latestByStudent.put(key, s);
                }
            }
            List<Submission> latestSubmissions = new ArrayList<>(latestByStudent.values());

            long submittedCount = latestByStudent.size();
            int totalSubmissions = latestSubmissions.size();

            double avgScore = latestSubmissions.stream()
                    .filter(s -> s.getTotalScore() != null)
                    .mapToInt(Submission::getTotalScore)
                    .average()
                    .orElse(0);

            item.put("submittedCount", submittedCount);
            item.put("totalSubmissions", totalSubmissions);
            item.put("avgScore", Math.round(avgScore * 10.0) / 10.0);

            // 判断过期
            boolean expired = task.getDeadline() != null && task.getDeadline().isBefore(LocalDateTime.now());
            item.put("expired", expired);

            result.add(item);
        }

        return Result.success(result);
    }

    /**
     * 获取单个作业详情（含成绩分布和提交列表）
     */
    @GetMapping("/{id}")
    public Result getTaskDetail(@PathVariable Long id) {
        HomeworkTask task = taskMapper.selectById(id);
        if (task == null) {
            return Result.error(404, "作业不存在");
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("id", task.getId());
        result.put("classId", task.getClassId());
        result.put("taskName", task.getTaskName());
        result.put("description", task.getDescription());
        result.put("deadline", task.getDeadline());
        result.put("allowLate", task.getAllowLate());
        result.put("latePenalty", task.getLatePenalty());
        result.put("status", task.getStatus());

        // 提交列表（每个学生只取最新）
        List<Submission> submissions = submissionMapper.selectList(
                new LambdaQueryWrapper<Submission>()
                        .eq(Submission::getTaskId, task.getId())
                        .orderByDesc(Submission::getSubmittedAt)
        );

        // 按学号去重，只保留最新提交
        Map<String, Submission> latestByStudent = new LinkedHashMap<>();
        for (Submission s : submissions) {
            String key = s.getStudentId() != null ? s.getStudentId() : s.getStudentName();
            if (!latestByStudent.containsKey(key)) {
                latestByStudent.put(key, s);
            }
        }
        List<Submission> latestSubmissions = new ArrayList<>(latestByStudent.values());

        // 成绩分布
        Map<String, Integer> dist = new LinkedHashMap<>();
        dist.put("excellent", 0); // 90-100
        dist.put("good", 0);      // 80-89
        dist.put("medium", 0);    // 70-79
        dist.put("pass", 0);      // 60-69
        dist.put("fail", 0);      // <60

        List<Map<String, Object>> studentList = new ArrayList<>();

        for (Submission s : latestSubmissions) {
            if (s.getTotalScore() != null) {
                if (s.getTotalScore() >= 90) dist.merge("excellent", 1, Integer::sum);
                else if (s.getTotalScore() >= 80) dist.merge("good", 1, Integer::sum);
                else if (s.getTotalScore() >= 70) dist.merge("medium", 1, Integer::sum);
                else if (s.getTotalScore() >= 60) dist.merge("pass", 1, Integer::sum);
                else dist.merge("fail", 1, Integer::sum);
            }

            Map<String, Object> si = new LinkedHashMap<>();
            si.put("studentName", s.getStudentName());
            si.put("studentId", s.getStudentId());
            si.put("score", s.getTotalScore());
            si.put("isLate", s.getIsLate() != null && s.getIsLate());
            si.put("penaltyApplied", s.getPenaltyApplied() != null && s.getPenaltyApplied());
            si.put("finalScore", s.getFinalScore());
            si.put("submittedAt", s.getSubmittedAt());
            studentList.add(si);
        }

        // 统计
        double avgScore = latestSubmissions.stream()
                .filter(s -> s.getTotalScore() != null)
                .mapToInt(Submission::getTotalScore)
                .average()
                .orElse(0);

        result.put("distribution", dist);
        result.put("submittedCount", latestByStudent.size());
        result.put("totalSubmissions", latestSubmissions.size());
        result.put("avgScore", Math.round(avgScore * 10.0) / 10.0);
        result.put("submissions", studentList);

        return Result.success(result);
    }

    /**
     * 关闭作业
     */
    @PutMapping("/{id}/close")
    public Result closeTask(@PathVariable Long id) {
        HomeworkTask task = taskMapper.selectById(id);
        if (task == null) {
            return Result.error(404, "作业不存在");
        }

        task.setStatus("closed");
        task.setUpdatedAt(LocalDateTime.now());
        taskMapper.updateById(task);

        return Result.success(null);
    }

    /**
     * 测试提醒功能（立即发送）
     */
    @PostMapping("/{id}/test-reminder")
    public Result testReminder(@PathVariable Long id) {
        HomeworkTask task = taskMapper.selectById(id);
        if (task == null) {
            return Result.error(404, "作业不存在");
        }

        String groupId = classInfoMapper.selectQqGroupIdById(task.getClassId());
        if (groupId == null || groupId.isEmpty()) {
            return Result.error(400, "班级未配置QQ群号");
        }

        // 查询班级学生总数和已提交数
        Integer totalStudents = classStudentMapper.countByClassId(task.getClassId());
        Integer submittedCount = classStudentMapper.countSubmittedByTaskId(task.getClassId(), task.getId());
        if (totalStudents == null) totalStudents = 0;
        if (submittedCount == null) submittedCount = 0;
        int unsubmittedCount = totalStudents - submittedCount;

        // 立即发送1小时前提醒（测试）
        taskReminderService.sendDeadlineReminder1h(task.getId());

        return Result.success(Map.of(
                "message", String.format("测试提醒已发送！班级共%d人，已交%d人，未交%d人", 
                        totalStudents, submittedCount, unsubmittedCount)
        ));
    }

    // ========== DTO ==========

    @Data
    public static class CreateTaskRequest {
        private Long classId;
        private String taskName;
        private String description;
        private LocalDateTime deadline;
        private Boolean allowLate;
        private Integer latePenalty;
    }
}
