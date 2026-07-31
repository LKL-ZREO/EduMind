package com.firedemo.edumind.homework;

import com.firedemo.edumind.teaching.QuestionDTO;
import com.firedemo.edumind.classroom.ClassInfo;
import com.firedemo.edumind.classroom.ClassService;
import com.firedemo.edumind.shared.result.Result;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.annotation.Transactional;
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
    private final HomeworkDraftService draftService;

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

    @GetMapping("/drafts")
    public Result<List<DraftResponse>> listDrafts() {
        Long userId = getCurrentUserId();
        if (userId == null) return Result.error(401, "Unauthorized");
        return Result.success(draftService.listByTeacherId(userId).stream()
                .map(this::toDraftResponse)
                .toList());
    }

    @GetMapping("/drafts/{id}")
    public Result<DraftResponse> getDraft(@PathVariable Long id) {
        Long userId = getCurrentUserId();
        if (userId == null) return Result.error(401, "Unauthorized");
        HomeworkDraft draft = draftService.getById(id);
        if (draft == null) return Result.error(404, "Draft not found");
        if (!userId.equals(draft.getTeacherId())) return Result.error(403, "Forbidden");
        return Result.success(toDraftResponse(draft));
    }

    @PostMapping("/drafts")
    @Transactional(rollbackFor = Exception.class)
    public Result<DraftResponse> createDraft(@RequestBody SaveDraftRequest req) {
        Long userId = getCurrentUserId();
        if (userId == null) return Result.error(401, "Unauthorized");

        HomeworkDraft draft = new HomeworkDraft();
        applyDraftRequest(draft, req);
        draft.setTeacherId(userId);
        draft.setStatus("draft");
        draft.setCreatedAt(LocalDateTime.now());
        draft.setUpdatedAt(LocalDateTime.now());
        draftService.create(draft);
        draftService.replaceQuestions(draft.getId(), userId, req.getQuestions());

        return Result.success(toDraftResponse(draftService.getById(draft.getId())));
    }

    @PutMapping("/drafts/{id}")
    @Transactional(rollbackFor = Exception.class)
    public Result<DraftResponse> updateDraft(@PathVariable Long id, @RequestBody SaveDraftRequest req) {
        Long userId = getCurrentUserId();
        if (userId == null) return Result.error(401, "Unauthorized");
        HomeworkDraft draft = draftService.getById(id);
        if (draft == null) return Result.error(404, "Draft not found");
        if (!userId.equals(draft.getTeacherId())) return Result.error(403, "Forbidden");

        applyDraftRequest(draft, req);
        draft.setUpdatedAt(LocalDateTime.now());
        draftService.update(draft);
        draftService.replaceQuestions(draft.getId(), userId, req.getQuestions());

        return Result.success(toDraftResponse(draftService.getById(draft.getId())));
    }

    @DeleteMapping("/drafts/{id}")
    @Transactional(rollbackFor = Exception.class)
    public Result<Void> deleteDraft(@PathVariable Long id) {
        Long userId = getCurrentUserId();
        if (userId == null) return Result.error(401, "Unauthorized");
        HomeworkDraft draft = draftService.getById(id);
        if (draft == null) return Result.success(null);
        if (!userId.equals(draft.getTeacherId())) return Result.error(403, "Forbidden");
        draftService.delete(id);
        return Result.success(null);
    }

    @PostMapping("/drafts/{id}/publish")
    @Transactional(rollbackFor = Exception.class)
    public Result<List<HomeworkTask>> publishDraft(@PathVariable Long id, @RequestBody PublishDraftRequest req) {
        Long userId = getCurrentUserId();
        if (userId == null) return Result.error(401, "Unauthorized");
        HomeworkDraft draft = draftService.getById(id);
        if (draft == null) return Result.error(404, "Draft not found");
        if (!userId.equals(draft.getTeacherId())) return Result.error(403, "Forbidden");
        if (req.getClassIds() == null || req.getClassIds().isEmpty()) {
            return Result.error(400, "classIds is required");
        }

        String taskName = blankToDefault(req.getTaskName(), draft.getTaskName());
        if (taskName == null || taskName.isBlank()) return Result.error(400, "taskName is required");
        String description = buildDescriptionFromDraft(draft);
        if (description.isBlank()) return Result.error(400, "questions are required");

        List<HomeworkTask> created = new ArrayList<>();
        for (Long classId : req.getClassIds()) {
            ClassInfo classInfo = classService.getClassById(classId);
            if (!userId.equals(classInfo.getTeacherId())) return Result.error(403, "Forbidden");

            HomeworkTask task = new HomeworkTask();
            task.setClassId(classId);
            task.setTaskName(taskName);
            task.setDescription(description);
            task.setDeadline(req.getDeadline() != null ? req.getDeadline() : draft.getDeadline());
            task.setAllowLate(req.getAllowLate() != null ? req.getAllowLate() : defaultBool(draft.getAllowLate(), true));
            task.setLatePenalty(req.getLatePenalty() != null ? req.getLatePenalty() : defaultInt(draft.getLatePenalty(), 0));
            task.setStatus("active");
            task.setCreatedBy(userId);
            task.setCreatedAt(LocalDateTime.now());
            task.setUpdatedAt(LocalDateTime.now());
            taskService.create(task);
            created.add(task);

            taskReminderService.sendTaskPublishedNotification(classId, taskName, task.getDeadline());
            taskReminderService.scheduleReminders(task.getId());
        }

        draft.setStatus("published");
        draft.setUpdatedAt(LocalDateTime.now());
        draftService.update(draft);
        return Result.success(created);
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

    @Data
    public static class SaveDraftRequest {
        private String taskName;
        private String description;
        private LocalDateTime deadline;
        private Boolean allowLate;
        private Integer latePenalty;
        private List<QuestionDTO> questions = new ArrayList<>();
    }

    @Data
    public static class PublishDraftRequest {
        private List<Long> classIds = new ArrayList<>();
        private String taskName;
        private LocalDateTime deadline;
        private Boolean allowLate;
        private Integer latePenalty;
    }

    @Data
    public static class DraftResponse {
        private Long id;
        private String taskName;
        private String description;
        private LocalDateTime deadline;
        private Boolean allowLate;
        private Integer latePenalty;
        private String status;
        private LocalDateTime createdAt;
        private LocalDateTime updatedAt;
        private List<QuestionDTO> questions = new ArrayList<>();
    }

    private void applyDraftRequest(HomeworkDraft draft, SaveDraftRequest req) {
        draft.setTaskName(req.getTaskName() != null ? req.getTaskName() : "");
        draft.setDescription(req.getDescription());
        draft.setDeadline(req.getDeadline());
        draft.setAllowLate(req.getAllowLate() != null ? req.getAllowLate() : true);
        draft.setLatePenalty(req.getLatePenalty() != null ? req.getLatePenalty() : 0);
    }

    private DraftResponse toDraftResponse(HomeworkDraft draft) {
        DraftResponse response = new DraftResponse();
        response.setId(draft.getId());
        response.setTaskName(draft.getTaskName());
        response.setDescription(draft.getDescription());
        response.setDeadline(draft.getDeadline());
        response.setAllowLate(draft.getAllowLate());
        response.setLatePenalty(draft.getLatePenalty());
        response.setStatus(draft.getStatus());
        response.setCreatedAt(draft.getCreatedAt());
        response.setUpdatedAt(draft.getUpdatedAt());
        response.setQuestions(draftService.listQuestions(draft.getId()));
        return response;
    }

    private String buildDescriptionFromDraft(HomeworkDraft draft) {
        List<QuestionDTO> questions = toDraftResponse(draft).getQuestions();
        StringBuilder html = new StringBuilder();
        for (int i = 0; i < questions.size(); i++) {
            QuestionDTO question = questions.get(i);
            html.append("""
                <section class="assignment-question">
                  <h3>\u9898\u76ee %d: %s</h3>
                  <div class="assignment-question-body">%s</div>
                  <p><strong>\u5206\u503c:</strong>%d; <strong>\u63d0\u4ea4\u65b9\u5f0f:</strong>%s</p>
                </section>
                """.formatted(
                    i + 1,
                    escapeHtml(blankToDefault(question.getTitle(), "")),
                    renderQuestionBody(question),
                    defaultInt(question.getScore(), 0),
                    defaultBool(question.getUploadRequired(), true)
                            ? "\u6309\u9898\u4e0a\u4f20\u9644\u4ef6"
                            : "\u5728\u7ebf\u4f5c\u7b54"
                ));
        }
        return html.toString();
    }

    private String renderQuestionBody(QuestionDTO question) {
        StringBuilder body = new StringBuilder();
        if (question.getRequirement() != null) body.append(question.getRequirement());
        if (question.getOptions() != null && !question.getOptions().isEmpty()) {
            body.append("<ol type=\"A\" class=\"assignment-options\">");
            for (QuestionDTO.OptionDTO option : question.getOptions()) {
                body.append("<li>")
                        .append(escapeHtml(blankToDefault(option.getText(), "")))
                        .append("</li>");
            }
            body.append("</ol>");
        }
        return body.toString();
    }

    private String escapeHtml(String value) {
        return value
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&#039;");
    }

    private String blankToDefault(String value, String defaultValue) {
        return value == null || value.isBlank() ? defaultValue : value;
    }

    private Boolean defaultBool(Boolean value, Boolean defaultValue) {
        return value != null ? value : defaultValue;
    }

    private Integer defaultInt(Integer value, Integer defaultValue) {
        return value != null ? value : defaultValue;
    }

    private Long getCurrentUserId() {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || auth.getDetails() == null) return null;
        if (auth.getDetails() instanceof Long uid) return uid;
        return null;
    }
}
