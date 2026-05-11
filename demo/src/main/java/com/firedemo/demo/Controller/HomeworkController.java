package com.firedemo.demo.Controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.firedemo.demo.DTO.EvaluationResultDTO;
import com.firedemo.demo.Entity.ClassInfo;
import com.firedemo.demo.Entity.HomeworkKnowledge;
import com.firedemo.demo.Entity.HomeworkTask;
import com.firedemo.demo.Entity.Submission;
import com.firedemo.demo.Service.FileStorageService;
import com.firedemo.demo.Service.OpenClawService;
import com.firedemo.demo.mapper.ClassInfoMapper;
import com.firedemo.demo.mapper.HomeworkKnowledgeMapper;
import com.firedemo.demo.mapper.HomeworkTaskMapper;
import com.firedemo.demo.mapper.SubmissionMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

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
    private final com.firedemo.demo.Service.OneBotHttpService oneBotHttpService;
    private final SubmissionMapper submissionMapper;
    private final ClassInfoMapper classInfoMapper;
    private final HomeworkKnowledgeMapper knowledgeMapper;
    private final HomeworkTaskMapper taskMapper;
    private final com.firedemo.demo.mapper.StudentQqBindingMapper studentQqBindingMapper;
    private final com.firedemo.demo.mapper.ClassStudentMapper classStudentMapper;
    private final ObjectMapper objectMapper;

    /** 文件名正则：学号_姓名_班级_作业名称.扩展名 */
    private static final Pattern FILE_NAME_PATTERN =
            Pattern.compile("^(.+)_(.+)_(.+)_(.+)\\.\\w+$");

    /**
     * 获取公开的班级列表（学生端使用）
     */
    @GetMapping("/classes")
    public ResponseEntity<?> getPublicClasses() {
        List<ClassInfo> classes = classInfoMapper.selectList(null);
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
        List<HomeworkTask> tasks = taskMapper.selectByClassId(classId);
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
        Integer count = submissionMapper.countByStudentIdAndTaskId(studentId, taskId);
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
     * 学生提交作业（公开接口，无需登录）
     *
     * @param file            作业文件
     * @param requirement     作业要求（可选）
     * @param expectedClassId 前端选中的班级ID（可选，用于校验）
     * @param expectedTaskId  前端选中的作业ID（可选，用于校验）
     * @param confirm         是否确认跳过校验（用于处理警告后直接提交）
     * @return 提交结果
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

        // 1. 解析文件名（新格式：学号_姓名_班级_作业名称.扩展名）
        Matcher matcher = FILE_NAME_PATTERN.matcher(originalFileName);
        if (!matcher.matches()) {
            return ResponseEntity.badRequest().body(Map.of(
                    "code", 400,
                    "message", "文件名格式错误，请使用「学号_姓名_班级_作业名称.扩展名」格式，例如：202103001_张三_计科2101_第三次作业.pdf"
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

        // 1.5 检查QQ号绑定（首次提交需要绑定）
        String qqNumber = studentQqBindingMapper.selectQqByStudentId(studentId);
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
            Integer existingCount = submissionMapper.countByStudentIdAndTaskId(studentId, expectedTaskId);
            if (existingCount == null) existingCount = 0;
            if (existingCount >= 3) {
                return ResponseEntity.badRequest().body(Map.of(
                        "code", 400,
                        "message", "该学号已提交3次，次数已达上限"
                ));
            }
        }

        // 2. 校验班级是否存在
        ClassInfo classInfo = classInfoMapper.selectOne(
                new LambdaQueryWrapper<ClassInfo>()
                        .eq(ClassInfo::getName, className)
        );

        if (classInfo == null) {
            return ResponseEntity.badRequest().body(Map.of(
                    "code", 400,
                    "message", "班级「" + className + "」不存在，请联系教师创建班级"
            ));
        }

        // 3. 文件名与选择项校验（非 confirm 时）
        if (!confirm && (expectedClassId != null || expectedTaskId != null)) {
            Map<String, Object> warnings = new LinkedHashMap<>();

            // 3a. 班级校验
            if (expectedClassId != null) {
                ClassInfo selectedClass = classInfoMapper.selectById(expectedClassId);
                if (selectedClass != null && !selectedClass.getName().equals(className)) {
                    warnings.put("classMismatch", Map.of(
                            "fileNameValue", className,
                            "selectedValue", selectedClass.getName()
                    ));
                }
            }

            // 3b. 作业名称校验（互相包含匹配）
            if (expectedTaskId != null) {
                HomeworkTask task = taskMapper.selectById(expectedTaskId);
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

        // 5. 读取文件内容
        String fileContent = fileStorageService.readFileContent(filePath);
        if (fileContent == null || fileContent.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of(
                    "code", 400, "message", "无法读取文件内容，请确保文件为文本格式"
            ));
        }

        // 6. 构造批改消息
        String message = String.format("""
            请批改以下作业，并以JSON格式返回结果：

            学生姓名：%s
            班级：%s
            作业名称：%s
            要求：%s

            文件内容：
            %s

            请使用批改作业助手的标准格式输出结果，必须是合法JSON格式。
            """, studentName, className, assignmentName,
                requirement != null ? requirement : "无特殊要求",
                fileContent);

        // 7. 调用 OpenClaw 批改
        String response;
        try {
            response = openClawService.chat(message, "homework_" + Instant.now().toEpochMilli());
        } catch (Exception e) {
            log.error("OpenClaw批改失败", e);
            return ResponseEntity.status(500).body(Map.of(
                    "code", 500, "message", "AI批改失败: " + e.getMessage()
            ));
        }

        // 8. 解析并保存
        Long submissionId;
        try {
            String jsonStr = extractJsonFromMarkdown(response);
            log.debug("提取到的JSON: jsonStr长度={}, 前100字符={}", jsonStr.length(), 
                jsonStr.substring(0, Math.min(100, jsonStr.length())));
            EvaluationResultDTO evaluation = objectMapper.readValue(jsonStr, EvaluationResultDTO.class);
            log.debug("解析成功: totalScore={}, strengths={}, contentScore={}", 
                evaluation.getTotalScore(), 
                evaluation.getStrengths() != null ? evaluation.getStrengths().size() : null,
                evaluation.getContentScore());

            // 计算提交次数和作业序号（按学号统计）
            Integer existingCount = expectedTaskId != null ? 
                    submissionMapper.countByStudentIdAndTaskId(studentId, expectedTaskId) : 0;
            if (existingCount == null) existingCount = 0;
            int submitCount = existingCount + 1;

            submissionId = saveSubmission(studentId, studentName, className, classInfo.getId(), assignmentName,
                    originalFileName, filePath, file.getSize(), evaluation, response,
                    expectedTaskId, submitCount);
        } catch (Exception e) {
            log.warn("解析评价JSON失败，保存原始响应: {}", e.getMessage(), e);
            submissionId = saveRawSubmission(studentId, studentName, className, classInfo.getId(), assignmentName,
                    originalFileName, filePath, (int) file.getSize(), response,
                    expectedTaskId, 1);
        }

        // 计算最终返回的提交次数和剩余次数
        Integer finalCount = expectedTaskId != null ? 
                submissionMapper.countByStudentIdAndTaskId(studentId, expectedTaskId) : 1;
        if (finalCount == null) finalCount = 1;
        int remainingAttempts = Math.max(0, 3 - finalCount);

        return ResponseEntity.ok(Map.of(
                "code", 200,
                "message", "作业提交成功",
                "data", Map.of(
                        "submissionId", submissionId,
                        "studentId", studentId,
                        "studentName", studentName,
                        "className", className,
                        "assignmentName", assignmentName,
                        "submitCount", finalCount,
                        "remainingAttempts", remainingAttempts
                )
        ));
    }

    /**
     * 保存批改结果
     */
    private Long saveSubmission(String studentId, String studentName, String className, Long classId,
                                 String assignmentName, String fileName, String filePath,
                                 long fileSize, EvaluationResultDTO evaluation, String rawResponse,
                                 Long taskId, int submitCount) {
        Submission submission = new Submission();
        submission.setStudentId(studentId);
        submission.setStudentName(studentName);
        submission.setClassName(className);
        submission.setClassId(classId);
        submission.setAssignmentName(assignmentName);
        submission.setFileName(fileName);
        submission.setFilePath(filePath);
        submission.setFileSize(fileSize);
        submission.setTotalScore(evaluation.getTotalScore());
        submission.setContentScore(evaluation.getContentScore() != null ? evaluation.getContentScore() : evaluation.getTotalScore());
        submission.setOverallComment(evaluation.getOverallComment());
        submission.setStrengths(evaluation.getStrengths() != null ?
                String.join(",", evaluation.getStrengths()) : "");
        submission.setWeaknesses(evaluation.getWeaknesses() != null ?
                String.join(",", evaluation.getWeaknesses()) : "");
        String suggestionsStr = "";
        if (evaluation.getSuggestions() != null) {
            suggestionsStr = evaluation.getSuggestions().stream()
                    .map(s -> s.getPriority() + ": " + s.getIssue() + " -> " + s.getSuggestion())
                    .reduce((a, b) -> a + "\n" + b)
                    .orElse("");
        }
        submission.setSuggestions(suggestionsStr);
        submission.setRawResponse(rawResponse);
        submission.setTaskId(taskId);
        submission.setSubmitCount(submitCount);
        submission.setRemainingAttempts(Math.max(0, 3 - submitCount));

        // 逾期判断
        if (taskId != null) {
            HomeworkTask task = taskMapper.selectById(taskId);
            if (task != null && task.getDeadline() != null && LocalDateTime.now().isAfter(task.getDeadline())) {
                submission.setIsLate(true);
                if (task.getAllowLate() != null && task.getAllowLate() && task.getLatePenalty() != null && task.getLatePenalty() > 0) {
                    submission.setPenaltyApplied(true);
                    int penalty = (int) Math.ceil(
                            java.time.Duration.between(task.getDeadline(), LocalDateTime.now()).toDays()
                    ) * task.getLatePenalty();
                    int finalScore = Math.max(0, (evaluation.getTotalScore() != null ? evaluation.getTotalScore() : 0) - penalty);
                    submission.setFinalScore(finalScore);
                }
            }
        }

        submission.setAssignmentNo(submitCount);

        submissionMapper.insert(submission);

        // 保存知识点掌握情况
        if (evaluation.getKnowledgePoints() != null) {
            for (EvaluationResultDTO.KnowledgePointItem kp : evaluation.getKnowledgePoints()) {
                HomeworkKnowledge hk = new HomeworkKnowledge();
                hk.setSubmissionId(submission.getId());
                hk.setKnowledgePoint(kp.getName());
                hk.setMastery(kp.getMastery());
                hk.setStatus(kp.getStatus());
                knowledgeMapper.insert(hk);
            }
        }

        log.info("学生作业提交已保存: studentName={}, className={}, assignmentName={}, totalScore={}, submitCount={}",
                studentName, className, assignmentName, evaluation.getTotalScore(), submitCount);

        // 自动加入班级学生名单
        if (classId != null && studentId != null && studentName != null) {
            classStudentMapper.insertIgnore(classId, studentId, studentName, "auto");
        }

        // 成绩不理想时发送QQ私聊提醒
        if (evaluation.getTotalScore() != null && evaluation.getTotalScore() < 60) {
            String qqNumber = studentQqBindingMapper.selectQqByStudentId(studentId);
            if (qqNumber != null) {
                oneBotHttpService.sendPrivateMessage(qqNumber, String.format(
                        "同学你好，你的作业「%s」得分 %d 分，成绩不太理想。建议多向老师或机器人提问，获取学习帮助。",
                        assignmentName, evaluation.getTotalScore()));
            }
        }

        return submission.getId();
    }

    /**
     * 保存无法解析JSON的原始响应
     */
    private Long saveRawSubmission(String studentId, String studentName, String className, Long classId,
                                    String assignmentName, String fileName, String filePath,
                                    long fileSize, String rawResponse,
                                    Long taskId, int submitCount) {
        Submission submission = new Submission();
        submission.setStudentId(studentId);
        submission.setStudentName(studentName);
        submission.setClassName(className);
        submission.setClassId(classId);
        submission.setAssignmentName(assignmentName);
        submission.setFileName(fileName);
        submission.setFilePath(filePath);
        submission.setFileSize(fileSize);
        submission.setRawResponse(rawResponse);
        submission.setTaskId(taskId);
        submission.setSubmitCount(submitCount);
        submission.setRemainingAttempts(Math.max(0, 3 - submitCount));
        submission.setAssignmentNo(submitCount);
        submissionMapper.insert(submission);

        log.info("学生作业提交已保存（原始响应）: studentName={}, className={}, assignmentName={}",
                studentName, className, assignmentName);
        return submission.getId();
    }

    /**
     * 从markdown代码块中提取JSON字符串
     */
    private String extractJsonFromMarkdown(String response) {
        if (response == null || response.isEmpty()) {
            return response;
        }
        String trimmed = response.trim();
        // 直接找第一个 { 和最后一个 }，不管前面有什么文字
        int jsonStart = trimmed.indexOf('{');
        int jsonEnd = trimmed.lastIndexOf('}');
        if (jsonStart != -1 && jsonEnd != -1 && jsonEnd > jsonStart) {
            trimmed = trimmed.substring(jsonStart, jsonEnd + 1);
            log.debug("extractJsonFromMarkdown 提取成功, json长度={}", trimmed.length());
            return trimmed;
        }
        log.warn("extractJsonFromMarkdown 未找到JSON花括号, raw开头={}", trimmed.substring(0, Math.min(100, trimmed.length())));
        return response;
    }

    // ============ QQ绑定接口 ============

    /**
     * 检查QQ号绑定状态
     */
    @GetMapping("/check-qq-binding")
    public ResponseEntity<?> checkQqBinding(@RequestParam String studentId) {
        String qqNumber = studentQqBindingMapper.selectQqByStudentId(studentId);
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

    /**
     * 绑定QQ号
     */
    @PostMapping("/bind-qq")
    public ResponseEntity<?> bindQq(@RequestBody BindQqRequest req) {
        // 校验QQ号格式
        if (req.getQqNumber() == null || !req.getQqNumber().matches("\\d{5,11}")) {
            return ResponseEntity.badRequest().body(Map.of(
                    "code", 400, "message", "QQ号格式不正确，应为5-11位数字"
            ));
        }

        // 保存绑定
        studentQqBindingMapper.insertOrUpdate(req.getStudentId(), req.getQqNumber(), req.getStudentName());
        return ResponseEntity.ok(Map.of("code", 200, "message", "绑定成功"));
    }

    @lombok.Data
    public static class BindQqRequest {
        private String studentId;
        private String studentName;
        private String qqNumber;
    }
}
