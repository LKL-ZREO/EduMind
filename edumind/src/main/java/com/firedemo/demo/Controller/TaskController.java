package com.firedemo.demo.Controller;

import com.firedemo.demo.Entity.ClassInfo;
import com.firedemo.demo.Entity.HomeworkTask;
import com.firedemo.demo.Entity.Submission;
import com.firedemo.demo.Service.ClassService;
import com.firedemo.demo.Service.HomeworkTaskService;
import com.firedemo.demo.Service.SubmissionService;
import com.firedemo.demo.Service.TaskReminderService;
import com.firedemo.demo.common.result.Result;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.context.SecurityContextHolder;
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

    private final HomeworkTaskService taskService;
    private final SubmissionService submissionService;
    private final ClassService classService;
    private final TaskReminderService taskReminderService;

    /**
     * 创建作业 — 需是该班级的教师
     */
    @PostMapping
    @PreAuthorize("@sec.isClassOwner(#req.classId)")
    public Result<HomeworkTask> createTask(@Valid @RequestBody CreateTaskRequest req) {
        Long userId = getCurrentUserId();

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

        taskService.create(task);
        log.info("创建作业: taskId={}, taskName={}, classId={}", task.getId(), req.getTaskName(), req.getClassId());

        taskReminderService.sendTaskPublishedNotification(req.getClassId(), req.getTaskName(), req.getDeadline());
        taskReminderService.scheduleReminders(task.getId());

        return Result.success(task);
    }

    @PutMapping("/{id}")
    @PreAuthorize("@sec.isTaskOwner(#id)")
    public Result<HomeworkTask> updateTask(@PathVariable Long id, @RequestBody CreateTaskRequest req) {
        HomeworkTask task = taskService.getById(id);

        task.setTaskName(req.getTaskName());
        task.setDescription(req.getDescription());
        task.setDeadline(req.getDeadline());
        task.setAllowLate(req.getAllowLate() != null ? req.getAllowLate() : task.getAllowLate());
        task.setLatePenalty(req.getLatePenalty() != null ? req.getLatePenalty() : task.getLatePenalty());
        task.setUpdatedAt(LocalDateTime.now());

        taskService.update(task);
        log.info("编辑作业: taskId={}", id);
        return Result.success(task);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("@sec.isTaskOwner(#id)")
    public Result<Void> deleteTask(@PathVariable Long id) {
        taskService.delete(id);
        log.info("删除作业: taskId={}", id);
        return Result.success(null);
    }

    @GetMapping
    @PreAuthorize("@sec.isClassOwner(#classId)")
    public Result<List<Map<String, Object>>> getTasks(@RequestParam Long classId) {
        List<HomeworkTask> tasks = taskService.listByClassId(classId);

        List<Map<String, Object>> taskStats = submissionService.listTaskStatsByClassId(classId);
        Map<Long, Map<String, Object>> statsMap = new HashMap<>();
        for (Map<String, Object> row : taskStats) {
            Long taskId = ((Number) row.get("task_id")).longValue();
            statsMap.put(taskId, row);
        }

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

            Map<String, Object> stats = statsMap.get(task.getId());
            if (stats != null) {
                Number count = (Number) stats.get("submitted_count");
                Number avg = (Number) stats.get("avg_score");
                item.put("submittedCount", count != null ? count.intValue() : 0);
                item.put("avgScore", avg != null ? avg.doubleValue() : 0.0);
            } else {
                item.put("submittedCount", 0);
                item.put("avgScore", 0.0);
            }
            item.put("totalSubmissions", item.get("submittedCount"));
            item.put("expired", task.getDeadline() != null && task.getDeadline().isBefore(LocalDateTime.now()));

            result.add(item);
        }
        return Result.success(result);
    }

    @GetMapping("/{id}")
    @PreAuthorize("@sec.isTaskOwner(#id)")
    public Result<Map<String, Object>> getTaskDetail(@PathVariable Long id) {
        HomeworkTask task = taskService.getById(id);
        if (task == null) return Result.error(404, "作业不存在");

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("id", task.getId());
        result.put("classId", task.getClassId());
        result.put("taskName", task.getTaskName());
        result.put("description", task.getDescription());
        result.put("deadline", task.getDeadline());
        result.put("allowLate", task.getAllowLate());
        result.put("latePenalty", task.getLatePenalty());
        result.put("status", task.getStatus());

        List<Submission> submissions = submissionService.listByTaskId(task.getId());
        Map<String, Submission> latestByStudent = new LinkedHashMap<>();
        for (Submission s : submissions) {
            String key = s.getStudentId() != null ? s.getStudentId() : s.getStudentName();
            if (!latestByStudent.containsKey(key)) {
                latestByStudent.put(key, s);
            }
        }
        List<Submission> latestSubmissions = new ArrayList<>(latestByStudent.values());

        Map<String, Integer> dist = new LinkedHashMap<>();
        dist.put("excellent", 0); dist.put("good", 0); dist.put("medium", 0);
        dist.put("pass", 0); dist.put("fail", 0);

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
            si.put("submissionId", s.getId());
            si.put("studentName", s.getStudentName());
            si.put("studentId", s.getStudentId());
            si.put("score", s.getTotalScore());
            si.put("isLate", s.getIsLate() != null && s.getIsLate());
            si.put("penaltyApplied", s.getPenaltyApplied() != null && s.getPenaltyApplied());
            si.put("finalScore", s.getFinalScore());
            si.put("submittedAt", s.getSubmittedAt());
            studentList.add(si);
        }

        double avgScore = latestSubmissions.stream()
                .filter(s -> s.getTotalScore() != null)
                .mapToInt(Submission::getTotalScore).average().orElse(0);

        result.put("distribution", dist);
        result.put("submittedCount", latestByStudent.size());
        result.put("totalSubmissions", latestSubmissions.size());
        result.put("avgScore", Math.round(avgScore * 10.0) / 10.0);
        result.put("submissions", studentList);
        return Result.success(result);
    }

    @PutMapping("/{id}/close")
    @PreAuthorize("@sec.isTaskOwner(#id)")
    public Result<Void> closeTask(@PathVariable Long id) {
        HomeworkTask task = taskService.getById(id);
        task.setStatus("closed");
        task.setUpdatedAt(LocalDateTime.now());
        taskService.update(task);
        return Result.success(null);
    }

    @PostMapping("/{id}/test-reminder")
    @PreAuthorize("@sec.isTaskOwner(#id)")
    public Result<Map<String, Object>> testReminder(@PathVariable Long id) {
        HomeworkTask task = taskService.getById(id);

        String groupId = classService.getQqGroupId(task.getClassId());
        if (groupId == null || groupId.isEmpty()) {
            return Result.error(400, "班级未配置QQ群号");
        }

        Integer totalStudents = classService.countStudentsByClassId(task.getClassId());
        Integer submittedCount = classService.countSubmittedByTaskId(task.getClassId(), task.getId());
        if (totalStudents == null) totalStudents = 0;
        if (submittedCount == null) submittedCount = 0;
        int unsubmittedCount = totalStudents - submittedCount;

        taskReminderService.sendDeadlineReminder1h(task.getId());

        return Result.success(Map.of(
                "message", String.format("测试提醒已发送！班级共%d人，已交%d人，未交%d人",
                        totalStudents, submittedCount, unsubmittedCount)
        ));
    }

    // ========== DTO ==========

    @Data
    public static class CreateTaskRequest {
        @NotNull(message = "班级ID不能为空")
        private Long classId;

        @NotBlank(message = "作业名称不能为空")
        private String taskName;

        private String description;
        private LocalDateTime deadline;
        private Boolean allowLate;
        private Integer latePenalty;
    }

    // ========== 内部工具 ==========

    private Long getCurrentUserId() {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || auth.getDetails() == null) return null;
        if (auth.getDetails() instanceof Long uid) return uid;
        return null;
    }
}
