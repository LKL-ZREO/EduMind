package com.firedemo.demo.config;

import com.firedemo.demo.Entity.*;
import com.firedemo.demo.mapper.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

/**
 * 资源归属校验器 — 配合 @PreAuthorize 使用，声明式校验"这个资源是不是你的"。
 *
 * <pre>
 * 使用方式：
 *   {@code @PreAuthorize("@sec.isClassOwner(#classId)")}
 *   {@code @PreAuthorize("@sec.isCourseOwner(#id)")}
 *   {@code @PreAuthorize("@sec.isTaskOwner(#id)")}
 *   {@code @PreAuthorize("@sec.isDocumentOwner(#docId)")}
 *   {@code @PreAuthorize("@sec.isDirectoryNodeOwner(#id)")}
 *   {@code @PreAuthorize("@sec.isSharedKbOwner(#id)")}
 *   {@code @PreAuthorize("@sec.isSubmissionOwner(#id)")}
 *   {@code @PreAuthorize("@sec.isTeacherKnowledgeOwner(#id)")}
 * </pre>
 */
@Slf4j
@Component("sec")
@RequiredArgsConstructor
public class OwnershipGuard {

    private final ClassInfoMapper classInfoMapper;
    private final CourseMapper courseMapper;
    private final HomeworkTaskMapper taskMapper;
    private final DocumentMapper documentMapper;
    private final DirectoryNodeMapper directoryNodeMapper;
    private final SharedKbMapper sharedKbMapper;
    private final SubmissionMapper submissionMapper;
    private final TeacherKnowledgeMapper teacherKnowledgeMapper;

    // ───────────────────── 班级 ─────────────────────

    public boolean isClassOwner(Long classId) {
        Long userId = getCurrentUserId();
        if (userId == null || classId == null) return false;
        ClassInfo ci = classInfoMapper.selectById(classId);
        if (ci == null) return false;
        return denyIfNotOwner(userId, ci.getTeacherId(), "班级", classId);
    }

    // ───────────────────── 课程 ─────────────────────

    public boolean isCourseOwner(Long courseId) {
        Long userId = getCurrentUserId();
        if (userId == null || courseId == null) return false;
        Course c = courseMapper.selectById(courseId);
        if (c == null) return false;
        return denyIfNotOwner(userId, c.getTeacherId(), "课程", courseId);
    }

    // ───────────────────── 作业任务 ─────────────────────

    public boolean isTaskOwner(Long taskId) {
        Long userId = getCurrentUserId();
        if (userId == null || taskId == null) return false;
        HomeworkTask task = taskMapper.selectById(taskId);
        if (task == null) return false;
        return denyIfNotOwner(userId, task.getCreatedBy(), "作业任务", taskId);
    }

    /**
     * 通过作业任务的 classId 校验（用于创建/查看作业时校验班级归属）
     */
    public boolean isTaskClassOwner(Long classId) {
        return isClassOwner(classId);
    }

    // ───────────────────── 文档 ─────────────────────

    public boolean isDocumentOwner(String docId) {
        Long userId = getCurrentUserId();
        if (userId == null || docId == null) return false;
        Document doc = documentMapper.selectById(docId);
        if (doc == null) return false;
        return denyIfNotOwner(userId, doc.getUserId(), "文档", docId);
    }

    // ───────────────────── 目录节点 ─────────────────────

    public boolean isDirectoryNodeOwner(Long nodeId) {
        Long userId = getCurrentUserId();
        if (userId == null || nodeId == null) return false;
        DirectoryNode node = directoryNodeMapper.selectById(nodeId);
        if (node == null) return false;
        return denyIfNotOwner(userId, node.getUserId(), "目录节点", nodeId);
    }

    // ───────────────────── 共享知识库 ─────────────────────

    public boolean isSharedKbOwner(Long kbId) {
        Long userId = getCurrentUserId();
        if (userId == null || kbId == null) return false;
        SharedKb kb = sharedKbMapper.selectById(kbId);
        if (kb == null) return false;
        return denyIfNotOwner(userId, kb.getOwnerId(), "共享知识库", kbId);
    }

    // ───────────────────── 提交记录 ─────────────────────

    public boolean isSubmissionOwner(Long submissionId) {
        if (submissionId == null) return false;
        Submission sub = submissionMapper.selectById(submissionId);
        if (sub == null) return false;
        return isClassOwner(sub.getClassId());
    }

    // ───────────────────── 教师知识点 ─────────────────────

    public boolean isTeacherKnowledgeOwner(Long knowledgeId) {
        if (knowledgeId == null) return false;
        TeacherKnowledge tk = teacherKnowledgeMapper.selectById(knowledgeId);
        if (tk == null) return false;
        return isClassOwner(tk.getClassId());
    }

    // ───────────────────── 内部工具 ─────────────────────

    private boolean denyIfNotOwner(Long userId, Long ownerId, String resourceType, Object resourceId) {
        boolean ok = userId.equals(ownerId);
        if (!ok) {
            log.warn("越权访问拦截: userId={} → {}={} ownerId={}", userId, resourceType, resourceId, ownerId);
        }
        return ok;
    }

    /**
     * 从 Spring Security 上下文获取当前登录用户 ID。
     */
    private Long getCurrentUserId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()) return null;
        Object details = auth.getDetails();
        if (details instanceof Long userId) return userId;
        return null;
    }
}
