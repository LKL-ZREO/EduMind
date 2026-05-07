package com.firedemo.demo.Controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.firedemo.demo.DTO.EvaluationResultDTO;
import com.firedemo.demo.Entity.ClassInfo;
import com.firedemo.demo.Entity.HomeworkKnowledge;
import com.firedemo.demo.Entity.Submission;
import com.firedemo.demo.Service.FileStorageService;
import com.firedemo.demo.Service.OpenClawService;
import com.firedemo.demo.mapper.ClassInfoMapper;
import com.firedemo.demo.mapper.HomeworkKnowledgeMapper;
import com.firedemo.demo.mapper.SubmissionMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.time.Instant;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

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
    private final SubmissionMapper submissionMapper;
    private final ClassInfoMapper classInfoMapper;
    private final HomeworkKnowledgeMapper knowledgeMapper;
    private final ObjectMapper objectMapper;

    /** 文件名正则：姓名_班级_作业名称.扩展名 */
    private static final Pattern FILE_NAME_PATTERN =
            Pattern.compile("^(.+)_(.+)_(.+)\\.\\w+$");

    /**
     * 学生提交作业（公开接口，无需登录）
     *
     * @param file       作业文件
     * @param requirement 作业要求（可选）
     * @return 提交结果
     */
    @PostMapping("/submit")
    public ResponseEntity<?> submitHomework(
            @RequestParam("file") @NotNull MultipartFile file,
            @RequestParam(value = "requirement", required = false) String requirement) {

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
                    "message", "文件名格式错误，请使用「姓名_班级_作业名称.扩展名」格式，例如：张三_计科2101_第三次作业.pdf"
            ));
        }

        String studentName = matcher.group(1).trim();
        String className = matcher.group(2).trim();
        String assignmentName = matcher.group(3).trim();

        if (studentName.isEmpty() || className.isEmpty() || assignmentName.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of(
                    "code", 400, "message", "文件名各部分不能为空"
            ));
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

        // 3. 保存文件到磁盘
        String filePath;
        try {
            filePath = fileStorageService.storeFile(file);
        } catch (Exception e) {
            log.error("文件保存失败", e);
            return ResponseEntity.status(500).body(Map.of(
                    "code", 500, "message", "文件保存失败: " + e.getMessage()
            ));
        }

        // 4. 读取文件内容
        String fileContent = fileStorageService.readFileContent(filePath);
        if (fileContent == null || fileContent.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of(
                    "code", 400, "message", "无法读取文件内容，请确保文件为文本格式"
            ));
        }

        // 5. 构造批改消息
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

        // 6. 调用 OpenClaw 批改
        String response;
        try {
            response = openClawService.chat(message, "homework_" + Instant.now().toEpochMilli());
        } catch (Exception e) {
            log.error("OpenClaw批改失败", e);
            return ResponseEntity.status(500).body(Map.of(
                    "code", 500, "message", "AI批改失败: " + e.getMessage()
            ));
        }

        // 7. 解析并保存
        Long submissionId;
        try {
            String jsonStr = extractJsonFromMarkdown(response);
            EvaluationResultDTO evaluation = objectMapper.readValue(jsonStr, EvaluationResultDTO.class);
            submissionId = saveSubmission(studentName, className, classInfo.getId(), assignmentName,
                    originalFileName, filePath, file.getSize(), evaluation, response);
        } catch (Exception e) {
            log.warn("解析评价JSON失败，保存原始响应: {}", e.getMessage());
            // 保存原始响应，无结构化数据
            submissionId = saveRawSubmission(studentName, className, classInfo.getId(), assignmentName,
                    originalFileName, filePath, file.getSize(), response);
        }

        return ResponseEntity.ok(Map.of(
                "code", 200,
                "message", "作业提交成功",
                "data", Map.of(
                        "submissionId", submissionId,
                        "studentName", studentName,
                        "className", className,
                        "assignmentName", assignmentName
                )
        ));
    }

    /**
     * 保存批改结果到 submission 表和 homework_knowledge 表
     */
    private Long saveSubmission(String studentName, String className, Long classId,
                                 String assignmentName, String fileName, String filePath,
                                 long fileSize, EvaluationResultDTO evaluation, String rawResponse) {
        Submission submission = new Submission();
        submission.setStudentName(studentName);
        submission.setClassName(className);
        submission.setClassId(classId);
        submission.setAssignmentName(assignmentName);
        submission.setFileName(fileName);
        submission.setFilePath(filePath);
        submission.setFileSize(fileSize);
        submission.setTotalScore(evaluation.getTotalScore());
        submission.setContentScore(evaluation.getTotalScore());
        submission.setOverallComment(evaluation.getOverallComment());
        submission.setStrengths(evaluation.getHighlights() != null ?
                String.join(",", evaluation.getHighlights()) : "");
        String suggestionsStr = "";
        if (evaluation.getSuggestions() != null) {
            suggestionsStr = evaluation.getSuggestions().stream()
                    .map(s -> s.getPriority() + ": " + s.getIssue() + " -> " + s.getSuggestion())
                    .reduce((a, b) -> a + "\n" + b)
                    .orElse("");
        }
        submission.setSuggestions(suggestionsStr);
        submission.setRawResponse(rawResponse);

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

        log.info("学生作业提交已保存: studentName={}, className={}, assignmentName={}, totalScore={}",
                studentName, className, assignmentName, evaluation.getTotalScore());
        return submission.getId();
    }

    /**
     * 保存无法解析JSON的原始响应
     */
    private Long saveRawSubmission(String studentName, String className, Long classId,
                                    String assignmentName, String fileName, String filePath,
                                    long fileSize, String rawResponse) {
        Submission submission = new Submission();
        submission.setStudentName(studentName);
        submission.setClassName(className);
        submission.setClassId(classId);
        submission.setAssignmentName(assignmentName);
        submission.setFileName(fileName);
        submission.setFilePath(filePath);
        submission.setFileSize(fileSize);
        submission.setRawResponse(rawResponse);
        submissionMapper.insert(submission);

        log.info("学生作业提交已保存（原始响应）: studentName={}, className={}, assignmentName={}",
                studentName, className, assignmentName);
        return submission.getId();
    }

    /**
     * 从markdown代码块中提取JSON字符串
     */
    /**
     * 从markdown代码块中提取JSON字符串
     * 处理 ```json ... ``` 包裹的JSON，以及前缀文本
     */
    private String extractJsonFromMarkdown(String response) {
        if (response == null || response.isEmpty()) {
            return response;
        }
        String trimmed = response.trim();
        // 查找第一个 ```json 或 ```
        int start = trimmed.indexOf("```json");
        if (start == -1) {
            start = trimmed.indexOf("```");
        }
        if (start != -1) {
            // 跳过 ``` 标记
            trimmed = trimmed.substring(start);
            int firstNewline = trimmed.indexOf('\n');
            if (firstNewline != -1) {
                trimmed = trimmed.substring(firstNewline + 1).trim();
            } else {
                trimmed = trimmed.substring(3).trim();
            }
        }
        // 去掉结尾的 ```
        if (trimmed.endsWith("```")) {
            trimmed = trimmed.substring(0, trimmed.length() - 3).trim();
        }
        // 尝试找到 JSON 的完整范围（从 { 到最后一个 }）
        int jsonStart = trimmed.indexOf('{');
        int jsonEnd = trimmed.lastIndexOf('}');
        if (jsonStart != -1 && jsonEnd != -1 && jsonEnd > jsonStart) {
            trimmed = trimmed.substring(jsonStart, jsonEnd + 1);
        }
        return trimmed;
    }
}
