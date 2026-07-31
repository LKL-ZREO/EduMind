package com.firedemo.edumind.live.service;

import com.firedemo.edumind.classroom.ClassStudent;
import com.firedemo.edumind.live.LiveAttendanceLog;
import com.firedemo.edumind.classroom.ClassStudentMapper;
import com.firedemo.edumind.live.ClassroomSessionMapper;
import com.firedemo.edumind.live.LiveAttendanceLogMapper;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Lazy;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.*;

@Slf4j
@Service
public class StudentPresenceService {

    private final SimpMessagingTemplate messagingTemplate;
    private final ClassStudentMapper classStudentMapper;
    private final ClassroomSessionMapper sessionMapper;
    private final LiveAttendanceLogMapper attendanceLogMapper;
    private final Map<Long, Set<StudentInfo>> onlineStudents = new ConcurrentHashMap<>();

    /** 断线宽限期执行器 */
    private final ScheduledExecutorService graceScheduler =
            Executors.newScheduledThreadPool(2, Thread.ofPlatform().name("presence-grace-", 0).factory());

    /** sessionId → (studentId → 待执行的离场任务) */
    private final Map<Long, Map<String, ScheduledFuture<?>>> pendingLeaves = new ConcurrentHashMap<>();

    private static final long GRACE_SECONDS = 30;

    public StudentPresenceService(@Lazy SimpMessagingTemplate messagingTemplate,
                                  ClassStudentMapper classStudentMapper,
                                  ClassroomSessionMapper sessionMapper,
                                  LiveAttendanceLogMapper attendanceLogMapper) {
        this.messagingTemplate = messagingTemplate;
        this.classStudentMapper = classStudentMapper;
        this.sessionMapper = sessionMapper;
        this.attendanceLogMapper = attendanceLogMapper;
    }

    public void studentJoined(Long sessionId, String studentId, String studentName) {
        // 取消待执行的离场任务（断线宽限期内重连）
        Map<String, ScheduledFuture<?>> pending = pendingLeaves.get(sessionId);
        if (pending != null) {
            ScheduledFuture<?> future = pending.remove(studentId);
            if (future != null) {
                future.cancel(false);
                if (pending.isEmpty()) pendingLeaves.remove(sessionId);
                log.debug("学生宽限期内重连: sessionId={}, student={}", sessionId, studentName);
                broadcast(sessionId);
                return;  // 已在在线集合中，无需重复添加
            }
        }
        // 正常加入
        onlineStudents.computeIfAbsent(sessionId, k -> ConcurrentHashMap.newKeySet())
                .add(new StudentInfo(studentId, studentName));
        broadcast(sessionId);
        // 记录出勤
        try {
            attendanceLogMapper.insert(LiveAttendanceLog.join(sessionId, getClassId(sessionId), studentId, studentName));
        } catch (Exception e) { log.warn("出勤记录写入失败(JOIN): {}", e.getMessage()); }
        log.debug("学生上线: sessionId={}, student={}", sessionId, studentName);
    }

    /** 学生 WebSocket 断线，启动 30 秒宽限期再移除 */
    public void studentDisconnected(Long sessionId, String studentId, String studentName) {
        if (!isActiveSession(sessionId)) return;
        ScheduledFuture<?> future = graceScheduler.schedule(() -> {
            pendingLeaves.getOrDefault(sessionId, Map.of()).remove(studentId);
            if (isActiveSession(sessionId)) {
                studentLeft(sessionId, studentId, studentName);
                log.debug("宽限期到，学生离场: sessionId={}, student={}", sessionId, studentName);
            }
        }, GRACE_SECONDS, TimeUnit.SECONDS);
        pendingLeaves.computeIfAbsent(sessionId, k -> new ConcurrentHashMap<>())
                .put(studentId, future);
        log.debug("学生断线，{}秒宽限期: sessionId={}, student={}", GRACE_SECONDS, sessionId, studentName);
    }

    /** 立即移除（宽限期到后实际执行） */
    private void studentLeft(Long sessionId, String studentId, String studentName) {
        Set<StudentInfo> set = onlineStudents.get(sessionId);
        if (set != null) {
            set.remove(new StudentInfo(studentId, studentName));
            if (set.isEmpty()) onlineStudents.remove(sessionId);
        }
        broadcast(sessionId);
        // 记录离场
        try {
            attendanceLogMapper.insert(LiveAttendanceLog.leave(sessionId, getClassId(sessionId), studentId, studentName));
        } catch (Exception e) { log.warn("出勤记录写入失败(LEAVE): {}", e.getMessage()); }
    }

    /** sessionId→classId 缓存，避免出勤记录时重复查 DB */
    private final Map<Long, Long> sessionClassCache = new ConcurrentHashMap<>();
    private Long getClassId(Long sessionId) {
        return sessionClassCache.computeIfAbsent(sessionId, sid -> {
            var s = sessionMapper.selectById(sid);
            return s != null ? s.getClassId() : null;
        });
    }

    /** 课堂结束时清理：取消等待中的宽限期任务 + 清空在线列表 */
    public void clearSession(Long sessionId) {
        Map<String, ScheduledFuture<?>> pending = pendingLeaves.remove(sessionId);
        if (pending != null) {
            pending.values().forEach(f -> f.cancel(false));
        }
        onlineStudents.remove(sessionId);
        sessionClassCache.remove(sessionId);
        log.debug("Presence 已清理: sessionId={}", sessionId);
    }

    private boolean isActiveSession(Long sessionId) {
        var session = sessionMapper.selectById(sessionId);
        return session != null && "ACTIVE".equals(session.getStatus());
    }

    @PreDestroy
    public void shutdown() {
        pendingLeaves.values().forEach(tasks -> tasks.values().forEach(task -> task.cancel(false)));
        pendingLeaves.clear();
        graceScheduler.shutdownNow();
    }

    public List<Map<String, String>> getOnlineStudents(Long sessionId) {
        Set<StudentInfo> set = onlineStudents.get(sessionId);
        if (set == null) return List.of();
        return set.stream().map(s -> Map.of("studentId", s.studentId, "studentName", s.studentName)).toList();
    }

    /** 获取缺席学生（已注册班级但未连接WebSocket） */
    public List<Map<String, String>> getAbsentStudents(Long sessionId) {
        Set<StudentInfo> online = onlineStudents.get(sessionId);
        Set<String> onlineIds = online != null ? online.stream().map(s -> s.studentId).collect(java.util.stream.Collectors.toSet()) : Set.of();
        var session = sessionMapper.selectById(sessionId);
        if (session == null) return List.of();
        List<ClassStudent> all = classStudentMapper.selectByClassId(session.getClassId());
        return all.stream()
                .filter(cs -> !onlineIds.contains(cs.getStudentId()))
                .map(cs -> Map.of("studentId", cs.getStudentId(), "studentName", cs.getStudentName()))
                .toList();
    }

    private void broadcast(Long sessionId) {
        List<Map<String, String>> online = getOnlineStudents(sessionId);
        List<Map<String, String>> absent = getAbsentStudents(sessionId);
        String dest = "/topic/session/" + sessionId + "/students";
        messagingTemplate.convertAndSend(dest, (Object) Map.of(
                "count", online.size(), "students", online,
                "absentCount", absent.size(), "absentStudents", absent));
    }

    record StudentInfo(String studentId, String studentName) {}
}
