package com.firedemo.demo.Controller;

import com.firedemo.demo.Entity.Course;
import com.firedemo.demo.Service.CourseService;
import com.firedemo.demo.common.result.Result;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 课程管理控制器
 */
@Slf4j
@RestController
@RequestMapping("/api/courses")
@RequiredArgsConstructor
public class CourseController {

    private final CourseService courseService;

    /** 获取当前教师的课程列表 — 天然按 userId 过滤 */
    @GetMapping
    public Result<List<Course>> list() {
        Long userId = getCurrentUserId();
        if (userId == null) return Result.error(401, "未登录");
        return Result.success(courseService.listByTeacherId(userId));
    }

    @GetMapping("/presets")
    public Result<Map<String, CourseService.PresetTemplate>> presets() {
        return Result.success(courseService.getPresets());
    }

    @PostMapping
    public Result<Course> create(@Valid @RequestBody CreateCourseRequest req) {
        Long userId = getCurrentUserId();
        if (userId == null) return Result.error(401, "未登录");

        String prompt = req.getSystemPrompt();
        if (req.getPresetKey() != null && !req.getPresetKey().isEmpty()) {
            CourseService.PresetTemplate preset = courseService.getPreset(req.getPresetKey());
            if (preset != null) {
                prompt = preset.prompt().replace("{{courseName}}", req.getName());
            }
        }
        if (prompt == null || prompt.isBlank()) {
            prompt = "你是教学助手，服务于" + req.getName() + "课程。";
        }

        Course course = courseService.create(userId, req.getName(), prompt,
                req.getKnowledgeScope() != null ? req.getKnowledgeScope() : "");
        return Result.success(course);
    }

    @PutMapping("/{id}")
    @PreAuthorize("@sec.isCourseOwner(#id)")
    public Result<Void> update(@PathVariable Long id, @Valid @RequestBody UpdateCourseRequest req) {
        Long userId = getCurrentUserId();
        courseService.update(id, userId, req.getName(), req.getSystemPrompt(), req.getKnowledgeScope());
        return Result.success(null);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("@sec.isCourseOwner(#id)")
    public Result<Void> delete(@PathVariable Long id) {
        Long userId = getCurrentUserId();
        courseService.delete(id, userId);
        return Result.success(null);
    }

    // ==================== DTO ====================

    @Data
    public static class CreateCourseRequest {
        @NotBlank(message = "课程名称不能为空")
        private String name;
        private String presetKey;
        private String systemPrompt;
        private String knowledgeScope;
    }

    @Data
    public static class UpdateCourseRequest {
        @NotBlank(message = "课程名称不能为空")
        private String name;
        private String systemPrompt;
        private String knowledgeScope;
    }

    // ==================== 内部工具 ====================

    private Long getCurrentUserId() {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || auth.getDetails() == null) return null;
        if (auth.getDetails() instanceof Long uid) return uid;
        return null;
    }
}
