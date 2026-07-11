package com.firedemo.demo.Controller;

import com.firedemo.demo.DTO.*;
import com.firedemo.demo.Entity.ClassInfo;
import com.firedemo.demo.Entity.ClassStudent;
import com.firedemo.demo.Service.ClassService;
import com.firedemo.demo.common.result.Result;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 班级管理控制器（教师端）
 */
@Slf4j
@RestController
@RequestMapping("/api/teacher/classes")
@RequiredArgsConstructor
public class ClassController {

    private final ClassService classService;

    /**
     * 获取当前教师的班级列表（按课程分组）— 天然按 userId 过滤，无需 @PreAuthorize
     */
    @GetMapping
    public Result<List<ClassGroupDTO>> listClasses() {
        Long userId = getCurrentUserId();
        if (userId == null) return Result.error(401, "未登录");
        return Result.success(classService.listGroupedByCourse(userId));
    }

    /**
     * 创建班级
     */
    @PostMapping
    public Result<ClassDetailDTO> createClass(@Valid @RequestBody CreateClassDTO dto) {
        Long userId = getCurrentUserId();
        if (userId == null) return Result.error(401, "未登录");
        ClassInfo ci = classService.createClass(userId, dto);
        return Result.success(toDetailDTO(ci));
    }

    /**
     * 获取班级详情（含学生列表）
     */
    @GetMapping("/{id}")
    @PreAuthorize("@sec.isClassOwner(#id)")
    public Result<Map<String, Object>> getClassDetail(@PathVariable Long id) {
        ClassInfo ci = classService.getClassById(id);
        List<ClassStudent> students = classService.listStudentsByClassId(id);
        Map<String, Object> result = new HashMap<>();
        result.put("class", toDetailDTO(ci));
        result.put("students", students);
        return Result.success(result);
    }

    @PutMapping("/{id}")
    @PreAuthorize("@sec.isClassOwner(#id)")
    public Result<Void> updateClass(@PathVariable Long id, @Valid @RequestBody UpdateClassDTO dto) {
        Long userId = getCurrentUserId();
        classService.updateClass(id, userId, dto);
        return Result.success(null);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("@sec.isClassOwner(#id)")
    public Result<Void> deleteClass(@PathVariable Long id) {
        Long userId = getCurrentUserId();
        classService.deleteClass(id, userId);
        return Result.success(null);
    }

    @PostMapping("/{id}/archive")
    @PreAuthorize("@sec.isClassOwner(#id)")
    public Result<Void> toggleArchive(@PathVariable Long id) {
        Long userId = getCurrentUserId();
        classService.toggleArchive(id, userId);
        return Result.success(null);
    }

    @DeleteMapping("/{id}/students/{studentId}")
    @PreAuthorize("@sec.isClassOwner(#id)")
    public Result<Void> removeStudent(@PathVariable Long id, @PathVariable String studentId) {
        Long userId = getCurrentUserId();
        classService.removeStudent(id, studentId, userId);
        return Result.success(null);
    }

    @PostMapping("/{id}/students/import")
    @PreAuthorize("@sec.isClassOwner(#id)")
    public Result<Map<String, Integer>> importStudents(@PathVariable Long id,
                                                        @Valid @RequestBody ImportStudentsDTO dto) {
        Long userId = getCurrentUserId();
        Map<String, Integer> result = classService.importStudents(id, userId, dto.getStudents());
        return Result.success(result);
    }

    /**
     * 通过邀请码加入班级（公开接口，无需登录）
     */
    @PostMapping("/join")
    public Result<Map<String, String>> joinByInvite(@Valid @RequestBody JoinByInviteDTO dto) {
        String className = classService.joinByInviteCode(
                dto.getInviteCode(), dto.getStudentId(), dto.getStudentName());
        Map<String, String> result = new HashMap<>();
        result.put("className", className);
        return Result.success(result);
    }

    // ========== 内部工具 ==========

    private Long getCurrentUserId() {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || auth.getDetails() == null) return null;
        if (auth.getDetails() instanceof Long uid) return uid;
        return null;
    }

    private ClassDetailDTO toDetailDTO(ClassInfo ci) {
        ClassDetailDTO dto = new ClassDetailDTO();
        dto.setId(ci.getId());
        dto.setName(ci.getName());
        dto.setCourseGroup(ci.getCourseGroup());
        dto.setCourseId(ci.getCourseId());
        dto.setQqGroupId(ci.getQqGroupId());
        dto.setDescription(ci.getDescription());
        dto.setInviteCode(ci.getInviteCode());
        dto.setStatus(ci.getStatus());
        dto.setCreatedAt(ci.getCreatedAt());
        dto.setUpdatedAt(ci.getUpdatedAt());
        return dto;
    }
}
