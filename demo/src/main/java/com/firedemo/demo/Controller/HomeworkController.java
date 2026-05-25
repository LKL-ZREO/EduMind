package com.firedemo.demo.Controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.firedemo.demo.common.async.GradingStreamProducer;
import com.firedemo.demo.DTO.EvaluationResultDTO;
import com.firedemo.demo.Entity.ClassInfo;
import com.firedemo.demo.Entity.HomeworkKnowledge;
import com.firedemo.demo.Entity.HomeworkTask;
import com.firedemo.demo.Entity.Submission;
import com.firedemo.demo.Service.FileStorageService;
import com.firedemo.demo.Service.OpenClawService;
import com.firedemo.demo.Service.TaskReminderService;
import com.firedemo.demo.Service.HomeworkResultService;import com.firedemo.demo.Service.HomeworkTaskService;
import com.firedemo.demo.Service.ClassService;import com.firedemo.demo.Service.SubmissionService;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.CacheEvict;

/**
 * 作业提交控制器（学生端公开接口，无需登录）
 */
@Slf4j
@RestController
@RequestMapping("/api/homework")
@RequiredArgsConstructor
public class HomeworkController {

    private final FileStorageService fileStorageService;
    private final OpenClawService openClawService;
    private final GradingStreamProducer gradingStreamProducer;
    private final com.firedemo.demo.Service.OneBotHttpService oneBotHttpService;
    private final SubmissionService submissionService;
    
    private final HomeworkResultService homeworkResultService;
    private final HomeworkTaskService taskService;
    private final ClassService classService;
private final TaskReminderService taskReminderService;
    private final CacheManager cacheManager;
    private final ObjectMapper objectMapper;

    /** 文件名正则：学号_姓名_班级_作业名称.扩展名 */
    private static final Pattern FILE_NAME_PATTERN =
            Pattern.compile("^(.+)_(.+)_(.+)_(.+)\\.\\w+$");

    /**
     * 获取公开的班级列表（学生端使用）
     */
    @GetMapping("/classes")
    public ResponseEntity<?> getPublicClasses() {
        List<ClassInfo> classes = classService.listAll();
        List<Map<String, Object>> result = classes.stream().map(c -> {
            Map<String, Object> m = new java.util.LinkedHashMap<>();
            m.put("id", c.getId());
            m.put("name", c.getName());
            return m;
        }).collect(java.util.stream.Collectors.toList());
        return ResponseEntity.ok(Map.of("code", 200, "data", result));
    }

    /**
     * 获取公开的作业列表（学生端使用，按班级筛选）
     */
    @GetMapping("/tasks")
    public ResponseEntity<?> getPublicTasks(@RequestParam Long classId) {
        List<HomeworkTask> tasks = taskService.listByClassId(classId);
        List<Map<String, Object>> result = tasks.stream().filter(t ->
                !"closed".equals(t.getStatus())
        ).map(t -> {
            Map<String, Object> m = new java.util.LinkedHashMap<>();
            m.put("id", t.getId());
            m.put("taskName", t.getTaskName());
            m.put("deadline", t.getDeadline());
            m.put("allowLate", t.getAllowLate());
            m.put("latePenalty", t.getLatePenalty());
            return m;
        }).collect(java.util.stream.Collectors.toList());
        return ResponseEntity.ok(Map.of("code", 200, "data", result));
    }

    /**
     * 获取学生提交状态（按学号查询）
     */
    @GetMapping("/submit-status")
    public ResponseEntity<?> getSubmitStatus(
            @RequestParam String studentId,
            @RequestParam Long taskId) {
        Integer count = submissionService.countByStudentIdAndTaskId(studentId, taskId);
        if (count == null) count = 0;
        int remaining = Math.max(0, 3 - count);
        return ResponseEntity.ok(Map.of(
                "code", 200,
                "data", Map.of(
                        "submitCount", count,
                        "remainingAttempts", remaining
                )
        ));
    }

    /**
     * 学生提交作业（异步批改）
     *
     * @param file            作业文件
     * @param requirement     作业要求（可选）
     * @param expectedClassId 前端选中的班级ID（可选，用于校验）
     * @param expectedTaskId  前端选中的作业ID（可选，用于校验）
     * @param confirm         是否确认跳过校验
     * @return 提交结果（含 submissionId，前端轮询获取批改结果）
     */
    @PostMapping("/submit")
    public ResponseEntity<?> submitHomework(
            @RequestParam("file") @NotNull MultipartFile file,
            @RequestParam(value = "requirement", required = false) String requirement,
            @RequestParam(value = "expectedClassId", required = false) Long expectedClassId,
            @RequestParam(value = "expectedTaskId", required = false) Long expectedTaskId,
            @RequestParam(value = "confirm", required = false, defaultValue = "false") boolean confirm) {

        String originalFileName = file.getOriginalFilename();
        if (originalFileName == null || originalFileName.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of(
                    "code", 400, "message", "文件名不能为空"
            ));
        }

        // 1. 解析文件名
        Matcher matcher = FILE_NAME_PATTERN.matcher(originalFileName);
        if (!matcher.matches()) {
            return ResponseEntity.badRequest().body(Map.of(
                    "code", 400,
                    "message", "文件名格式错误，请使用「学号_姓名_班级_作业名称.扩展名」格式"
            ));
        }

        String studentId = matcher.group(1).trim();
        String studentName = matcher.group(2).trim();
        String className = matcher.group(3).trim();
        String assignmentName = matcher.group(4).trim();

        if (studentId.isEmpty() || studentName.isEmpty() || className.isEmpty() || assignmentName.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of(
                    "code", 400, "message", "文件名各部分不能为空"
            ));
        }

        // 1.5 检查QQ号绑定
        String qqNumber = classService.getQqByStudentId(studentId);
        if (qqNumber == null) {
            return ResponseEntity.ok(Map.of(
                    "code", 401,
                    "message", "请先绑定QQ号",
                    "needBind", true,
                    "data", Map.of("studentId", studentId, "studentName", studentName)
            ));
        }

        // 1.6 检查提交次数限制
        if (expectedTaskId != null) {
            Integer existingCount = submissionService.countByStudentIdAndTaskId(studentId, expectedTaskId);
            if (existingCount == null) existingCount = 0;
            if (existingCount >= 3) {
                return ResponseEntity.badRequest().body(Map.of(
                        "code", 400,
                        "message", "该学号已提交3次，次数已达上限"
                ));
            }
        }

        // 2. 校验班级
        ClassInfo classInfo = classService.getClassByName(className);
        if (classInfo == null) {
            return ResponseEntity.badRequest().body(Map.of(
                    "code", 400,
                    "message", "班级「" + className + "」不存在，请联系教师创建班级"
            ));
        }

        // 3. 文件名与选择项校验
        if (!confirm && (expectedClassId != null || expectedTaskId != null)) {
            Map<String, Object> warnings = new LinkedHashMap<>();
            if (expectedClassId != null) {
                ClassInfo selectedClass = classService.getClassById(expectedClassId);
                if (selectedClass != null && !selectedClass.getName().equals(className)) {
                    warnings.put("classMismatch", Map.of(
                            "fileNameValue", className,
                            "selectedValue", selectedClass.getName()
                    ));
                }
            }
            if (expectedTaskId != null) {
                HomeworkTask task = taskService.getById(expectedTaskId);
                if (task != null) {
                    String taskName = task.getTaskName();
                    boolean matches = assignmentName.contains(taskName) || taskName.contains(assignmentName);
                    if (!matches) {
                        warnings.put("taskMismatch", Map.of(
                                "fileNameValue", assignmentName,
                                "selectedValue", taskName
                        ));
                    }
                }
            }
            if (!warnings.isEmpty()) {
                return ResponseEntity.ok(Map.of(
                        "code", 300,
                        "message", "文件名与选择项不匹配，请确认",
                        "data", Map.of(
                                "warnings", warnings,
                                "studentId", studentId,
                                "studentName", studentName,
                                "parsedClassName", className,
                                "parsedAssignmentName", assignmentName
                        )
                ));
            }
        }

        // 4. 保存文件到磁盘
        String filePath;
        try {
            filePath = fileStorageService.storeFile(file);
        } catch (Exception e) {
            log.error("文件保存失败", e);
            return ResponseEntity.status(500).body(Map.of(
                    "code", 500, "message", "文件保存失败: " + e.getMessage()
            ));
        }

        // 5. 读取文件内容（可选校验，不存储到DB）
        String fileContent = fileStorageService.readFileContent(filePath);
        if (fileContent == null || fileContent.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of(
                    "code", 400, "message", "无法读取文件内容，请确保文件为文本格式"
            ));
        }

        // 6. 计算提交次数
        Integer existingCount = expectedTaskId != null ?
                submissionService.countByStudentIdAndTaskId(studentId, expectedTaskId) : 0;
        if (existingCount == null) existingCount = 0;
        int submitCount = existingCount + 1;

        // 7. 创建 Submission（状态 PENDING），入库
        Submission submission = new Submission();
        submission.setStudentId(studentId);
        submission.setStudentName(studentName);
        submission.setClassName(className);
        submission.setClassId(classInfo.getId());
        submission.setAssignmentName(assignmentName);
        submission.setFileName(originalFileName);
        submission.setFilePath(filePath);
        submission.setFileSize(file.getSize());
        submission.setStatus("PENDING");
        submission.setTaskId(expectedTaskId);
        submission.setSubmitCount(submitCount);
        submission.setRemainingAttempts(Math.max(0, 3 - submitCount));
        submission.setAssignmentNo(submitCount);
        submissionService.create(submission);

        log.info("作业提交已入库(PENDING): submissionId={}, studentName={}, className={}, assignmentName={}",
                submission.getId(), studentName, className, assignmentName);

        // 8. 清除该班级 dashboard 缓存
        if (classInfo.getId() != null) {
            var cache = cacheManager.getCache("dashboard");
            if (cache != null) {
                cache.evict("metrics:" + classInfo.getId());
                cache.evict("scoreDist:" + classInfo.getId());
                cache.evict("knowledge:" + classInfo.getId());
                cache.evict("errors:" + classInfo.getId());
            }
        }

        // 9. 入队 Redis Stream（异步批改）
        gradingStreamProducer.sendTask(submission.getId(), requirement);

        int remainingAttempts = Math.max(0, 3 - submitCount);
        return ResponseEntity.ok(Map.of(
                "code", 200,
                "message", "作业已提交，正在排队批改中",
                "data", Map.of(
                        "submissionId", submission.getId(),
                        "studentId", studentId,
                        "studentName", studentName,
                        "className", className,
                        "assignmentName", assignmentName,
                        "submitCount", submitCount,
                        "remainingAttempts", remainingAttempts
                )
        ));
    }

    /**
     * 查询批改结果（前端轮询）
     *
     * @param submissionId 提交ID
     * @return 批改状态和结果
     */
    @GetMapping("/result/{submissionId}")
    public ResponseEntity<?> getGradingResult(@PathVariable Long submissionId) {
        Submission sub = submissionService.getById(submissionId);
        if (sub == null) {
            return ResponseEntity.notFound().build();
        }

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("submissionId", sub.getId());
        data.put("status", sub.getStatus() != null ? sub.getStatus() : "PENDING");
        data.put("studentName", sub.getStudentName());
        data.put("assignmentName", sub.getAssignmentName());
        data.put("submitCount", sub.getSubmitCount());
        data.put("remainingAttempts", sub.getRemainingAttempts());

        if ("COMPLETED".equals(sub.getStatus())) {
            data.put("totalScore", sub.getTotalScore());
            data.put("contentScore", sub.getContentScore());
            data.put("overallComment", sub.getOverallComment());
            data.put("strengths", sub.getStrengths() != null ?
                    java.util.Arrays.asList(sub.getStrengths().split(",")) : List.of());
            data.put("weaknesses", sub.getWeaknesses() != null ?
                    java.util.Arrays.asList(sub.getWeaknesses().split(",")) : List.of());
            data.put("suggestions", sub.getSuggestions());
            data.put("finalScore", sub.getFinalScore());
            data.put("isLate", sub.getIsLate());
            data.put("penaltyApplied", sub.getPenaltyApplied());
        } else if ("FAILED".equals(sub.getStatus())) {
            data.put("errorMessage", sub.getErrorMessage());
        }

        return ResponseEntity.ok(Map.of("code", 200, "data", data));
    }

    // ============ QQ绑定接口 ============

    @GetMapping("/check-qq-binding")
    public ResponseEntity<?> checkQqBinding(@RequestParam String studentId) {
        String qqNumber = classService.getQqByStudentId(studentId);
        if (qqNumber == null) {
            return ResponseEntity.ok(Map.of(
                    "code", 401,
                    "message", "请先绑定QQ号",
                    "needBind", true
            ));
        }
        return ResponseEntity.ok(Map.of(
                "code", 200,
                "data", Map.of("qqNumber", qqNumber)
        ));
    }

    @PostMapping("/bind-qq")
    public ResponseEntity<?> bindQq(@RequestBody BindQqRequest req) {
        if (req.getQqNumber() == null || !req.getQqNumber().matches("\\d{5,11}")) {
            return ResponseEntity.badRequest().body(Map.of(
                    "code", 400, "message", "QQ号格式不正确，应为5-11位数字"
            ));
        }
        classService.bindQq(req.getStudentId(), req.getQqNumber(), req.getStudentName());
        return ResponseEntity.ok(Map.of("code", 200, "message", "绑定成功"));
    }

    /**
     * OpenClaw cron 触发：对所有活跃作业发送完成情况播报
     */
    @GetMapping("/tasks/remind-all")
    public ResponseEntity<?> remindAllTasks() {
        List<HomeworkTask> activeTasks = taskService.listActiveWithDeadline();
        if (activeTasks.isEmpty()) {
            return ResponseEntity.ok(Map.of(
                    "code", 200,
                    "message", "无活跃作业，跳过"
            ));
        }

        int count = 0;
        for (HomeworkTask task : activeTasks) {
            try {
                taskReminderService.sendRecurringStatusReminder(task.getId());
                count++;
            } catch (Exception e) {
                log.error("播报失败: taskId={}", task.getId(), e);
            }
        }

        return ResponseEntity.ok(Map.of(
                "code", 200,
                "message", "已处理" + count + "/" + activeTasks.size() + "个作业"
        ));
    }

    @lombok.Data
    public static class BindQqRequest {
        private String studentId;
        private String studentName;
        private String qqNumber;
    }
}
