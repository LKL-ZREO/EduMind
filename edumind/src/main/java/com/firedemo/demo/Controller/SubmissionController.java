package com.firedemo.demo.Controller;

import com.firedemo.demo.Entity.Submission;
import com.firedemo.demo.Service.FileStorageService;
import com.firedemo.demo.Service.SubmissionService;
import com.firedemo.demo.common.result.Result;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 提交记录接口
 */
@Slf4j
@RestController
@RequestMapping("/api/submissions")
@RequiredArgsConstructor
public class SubmissionController {

    private final SubmissionService submissionService;
    private final FileStorageService fileStorageService;

    /**
     * 获取提交文件原内容（供新页签查看）
     */
    @GetMapping("/{id}/content")
    public Result getContent(@PathVariable Long id) {
        Submission sub = submissionService.getById(id);
        if (sub == null) {
            return Result.error(404, "提交记录不存在");
        }

        String content;
        try {
            content = fileStorageService.readFileContent(sub.getFilePath());
        } catch (Exception e) {
            log.error("读取文件失败: submissionId={}, path={}", id, sub.getFilePath(), e);
            return Result.error(500, "读取文件失败");
        }

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("submissionId", sub.getId());
        data.put("studentName", sub.getStudentName());
        data.put("fileName", sub.getFileName());
        data.put("content", content);

        return Result.success(data);
    }
}
