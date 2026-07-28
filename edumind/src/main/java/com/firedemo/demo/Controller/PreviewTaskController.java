package com.firedemo.demo.Controller;

import com.firedemo.demo.DTO.CreatePreviewRequest;
import com.firedemo.demo.DTO.PreviewTaskDTO;
import com.firedemo.demo.Service.PreviewTaskService;
import com.firedemo.demo.common.result.Result;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Slf4j
@RestController
@RequestMapping("/api/preview")
@RequiredArgsConstructor
public class PreviewTaskController {

    private final PreviewTaskService previewTaskService;

    /** 教师：AI 生成预习任务 */
    @PostMapping("/create")
    @PreAuthorize("@sec.isClassOwner(#req.classId)")
    public Result<PreviewTaskDTO> create(@Valid @RequestBody CreatePreviewRequest req) {
        Long userId = getCurrentUserId();
        if (userId == null) return Result.error(401, "未登录");
        return Result.success(previewTaskService.createPreviewTask(
                userId, req.getClassId(), req.getKnowledgePoint(), req.getTopic(), req.getDocId()));
    }

    /** 教师：查看班级预习任务列表 */
    @GetMapping("/list")
    @PreAuthorize("@sec.isClassOwner(#classId)")
    public Result<List<PreviewTaskDTO>> list(@RequestParam Long classId) {
        return Result.success(previewTaskService.listByClassId(classId));
    }

    /** 学生/教师：查看单个预习任务详情（公开，学生通过分享链接查看） */
    @GetMapping("/{taskId}")
    public Result<PreviewTaskDTO> detail(@PathVariable Long taskId) {
        return Result.success(previewTaskService.getById(taskId));
    }

    /** 教师：关闭预习任务 */
    @PostMapping("/{taskId}/close")
    @PreAuthorize("@sec.isPreviewTaskOwner(#taskId)")
    public Result<Void> close(@PathVariable Long taskId) {
        Long userId = getCurrentUserId();
        if (userId == null) return Result.error(401, "未登录");
        previewTaskService.closeTask(taskId, userId);
        return Result.success(null);
    }

    private Long getCurrentUserId() {
        try {
            Object details = SecurityContextHolder.getContext().getAuthentication().getDetails();
            if (details instanceof Long id) return id;
            if (details instanceof Integer i) return i.longValue();
        } catch (Exception e) {
            log.debug("读取当前用户ID失败: {}", e.getMessage());
        }
        return null;
    }
}
