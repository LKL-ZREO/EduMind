package com.firedemo.demo.Controller;

import com.firedemo.demo.Entity.Course;
import com.firedemo.demo.Service.CourseService;
import com.firedemo.demo.common.result.Result;
import com.firedemo.demo.utils.JwtUtil;
import jakarta.servlet.http.HttpServletRequest;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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
    private final JwtUtil jwtUtil;

    /** 获取当前教师的课程列表 */
    @GetMapping
    public Result<List<Course>> list(HttpServletRequest request) {
        Long userId = jwtUtil.getUserIdFromRequest(request);
        if (userId == null) return Result.error(401, "未登录");
        return Result.success(courseService.listByTeacherId(userId));
    }

    /** 预设模板列表 */
    @GetMapping("/presets")
    public Result<Map<String, CourseService.PresetTemplate>> presets() {
        return Result.success(courseService.getPresets());
    }

    /** 创建课程 */
    @PostMapping
    public Result<Course> create(@RequestBody CreateCourseRequest req,
                                  HttpServletRequest request) {
        Long userId = jwtUtil.getUserIdFromRequest(request);
        if (userId == null) return Result.error(401, "未登录");

        String prompt = req.getSystemPrompt();
        // 如果选了预设模板，用模板内容
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

    /** 更新课程（名称、Prompt、知识范围） */
    @PutMapping("/{id}")
    public Result<Void> update(@PathVariable Long id,
                                @RequestBody UpdateCourseRequest req,
                                HttpServletRequest request) {
        Long userId = jwtUtil.getUserIdFromRequest(request);
        if (userId == null) return Result.error(401, "未登录");
        courseService.update(id, userId, req.getName(), req.getSystemPrompt(),
                req.getKnowledgeScope());
        return Result.success(null);
    }

    /** 删除课程 */
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id,
                                HttpServletRequest request) {
        Long userId = jwtUtil.getUserIdFromRequest(request);
        if (userId == null) return Result.error(401, "未登录");
        courseService.delete(id, userId);
        return Result.success(null);
    }

    // ==================== DTO ====================

    @Data
    public static class CreateCourseRequest {
        private String name;
        private String presetKey;
        private String systemPrompt;
        private String knowledgeScope;
    }

    @Data
    public static class UpdateCourseRequest {
        private String name;
        private String systemPrompt;
        private String knowledgeScope;
    }
}
