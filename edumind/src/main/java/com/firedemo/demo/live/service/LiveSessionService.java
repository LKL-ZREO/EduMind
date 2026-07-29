package com.firedemo.demo.live.service;

import com.firedemo.demo.DTO.*;
import com.firedemo.demo.Entity.*;
import com.firedemo.demo.common.exception.BusinessException;
import com.firedemo.demo.common.exception.ErrorCode;
import com.firedemo.demo.live.security.LiveSessionTokenService;
import com.firedemo.demo.mapper.*;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class LiveSessionService {

    private final ClassroomSessionMapper sessionMapper;
    private final ClassInfoMapper classInfoMapper;
    private final ClassStudentMapper classStudentMapper;
    private final InteractionService interactionService;
    private final UserMapper userMapper;
    private final LiveSessionTokenService liveSessionTokenService;
    private final LiveNotificationService liveNotificationService;
    private final HandRaiseService handRaiseService;
    private final StudentPresenceService presenceService;
    private final SimpMessagingTemplate messagingTemplate;

    private static final String CHARS = "ABCDEFGHJKMNPQRSTUVWXYZ23456789";
    private static final SecureRandom RNG = new SecureRandom();

    @Transactional
    public ClassroomSession createSession(Long teacherId, LiveSessionCreateDTO dto) {
        ClassroomSession active = sessionMapper.findActiveByClassId(dto.getClassId());
        if (active != null && active.getTeacherId().equals(teacherId)) {
            log.info("复用已有课堂: sessionId={}", active.getId());
            return active;
        }
        ClassInfo classInfo = classInfoMapper.selectById(dto.getClassId());
        if (classInfo == null)
            throw new BusinessException(ErrorCode.DATA_NOT_FOUND.getCode(), "班级不存在");

        String code = generateCode();
        ClassroomSession session = ClassroomSession.builder()
                .classId(dto.getClassId()).teacherId(teacherId).sessionCode(code)
                .title(dto.getTitle() != null ? dto.getTitle() : classInfo.getName() + " 课堂")
                .courseId(dto.getCourseId()).status("ACTIVE").startedAt(LocalDateTime.now())
                .teacherOfflineAt(LocalDateTime.now()).build();
        sessionMapper.insert(session);
        log.info("课堂已创建: sessionId={}, code={}", session.getId(), code);
        // OneBot QQ 群开课通知
        liveNotificationService.notifySessionStart(session);
        return session;
    }

    @Transactional
    public LiveSessionInfoDTO joinSession(LiveJoinDTO dto) {
        String code = dto.getCode() != null ? dto.getCode().trim().toUpperCase() : "";
        String studentId = dto.getStudentId() != null ? dto.getStudentId().trim() : "";
        ClassroomSession session = sessionMapper.findByCode(code);
        if (session == null)
            throw new BusinessException(ErrorCode.DATA_NOT_FOUND.getCode(), "课堂不存在或已结束");

        // 有花名册时，只接收学号，姓名始终使用花名册中的标准值。
        int managedRosterCount = Optional.ofNullable(
                classStudentMapper.countManagedByClassId(session.getClassId())).orElse(0);
        ClassStudent rosterStudent = classStudentMapper.selectOne(
                new LambdaQueryWrapper<ClassStudent>()
                        .eq(ClassStudent::getClassId, session.getClassId())
                        .eq(ClassStudent::getStudentId, studentId));

        String studentName;
        if (rosterStudent != null) {
            studentName = rosterStudent.getStudentName();
        } else if (managedRosterCount > 0) {
            throw new BusinessException(ErrorCode.PARAM_ERROR.getCode(), "学号不在班级名单中，请联系老师添加");
        } else {
            studentName = dto.getStudentName() != null ? dto.getStudentName().trim() : "";
            if (studentName.isBlank()) {
                throw new BusinessException(ErrorCode.PARAM_ERROR.getCode(), "班级尚未建立花名册，首次加入请输入姓名");
            }
            classStudentMapper.insertIgnore(session.getClassId(), studentId, studentName, "live");
        }

        String token = liveSessionTokenService.issue(
                studentId, studentName, session.getId());
        ClassInfo classInfo = classInfoMapper.selectById(session.getClassId());
        String teacherName = "";
        if (classInfo != null && classInfo.getTeacherId() != null) {
            User teacher = userMapper.selectById(classInfo.getTeacherId());
            if (teacher != null) teacherName = teacher.getUsername();
        }

        return LiveSessionInfoDTO.builder()
                .sessionId(session.getId()).sessionCode(session.getSessionCode())
                .title(session.getTitle())
                .className(classInfo != null ? classInfo.getName() : "")
                .teacherName(teacherName).token(token)
                .studentId(studentId).studentName(studentName)
                .requiresStudentName(false).build();
    }

    @Transactional
    public void endSession(Long sessionId, Long teacherId) {
        ClassroomSession session = sessionMapper.selectById(sessionId);
        if (session == null) throw new BusinessException(ErrorCode.DATA_NOT_FOUND.getCode(), "课堂不存在");
        if (!session.getTeacherId().equals(teacherId))
            throw new BusinessException(ErrorCode.FORBIDDEN.getCode(), "无权结束此课堂");
        if (sessionMapper.endSession(sessionId) == 0) return;
        finishSession(session, "MANUAL");
    }

    /** 教师所有连接持续离线超过宽限期时，由清理任务调用。 */
    @Transactional
    public boolean autoEndSession(Long sessionId) {
        ClassroomSession session = sessionMapper.selectById(sessionId);
        if (session == null || !"ACTIVE".equals(session.getStatus())) return false;
        if (sessionMapper.endSession(sessionId) == 0) return false;
        finishSession(session, "TEACHER_OFFLINE_TIMEOUT");
        log.info("教师离线超时，课堂已自动结束: sessionId={}", sessionId);
        return true;
    }

    private void finishSession(ClassroomSession session, String reason) {
        Long sessionId = session.getId();
        interactionService.closeActiveInteraction(sessionId);

        // 通过 WebSocket 通知所有学生：课堂已结束
        messagingTemplate.convertAndSend("/topic/session/" + sessionId + "/teacher-status",
                (Object) Map.of("online", false, "sessionEnded", true, "reason", reason));

        // 清理举手队列 + 在线状态
        handRaiseService.clear(sessionId);
        presenceService.clearSession(sessionId);
        // OneBot QQ 课堂总结 + 缺席提醒
        liveNotificationService.notifySessionSummary(session);
    }

    public ClassroomSession getSession(Long sessionId) { return sessionMapper.selectById(sessionId); }

    public LiveSessionInfoDTO previewByCode(String code) {
        ClassroomSession session = sessionMapper.findByCode(code);
        if (session == null) throw new BusinessException(ErrorCode.DATA_NOT_FOUND.getCode(), "课堂不存在或已结束");
        ClassInfo classInfo = classInfoMapper.selectById(session.getClassId());
        String teacherName = "";
        if (classInfo != null && classInfo.getTeacherId() != null) {
            User teacher = userMapper.selectById(classInfo.getTeacherId());
            if (teacher != null) teacherName = teacher.getUsername();
        }
        return LiveSessionInfoDTO.builder()
                .sessionId(session.getId()).sessionCode(session.getSessionCode())
                .title(session.getTitle())
                .className(classInfo != null ? classInfo.getName() : "")
                .teacherName(teacherName)
                .requiresStudentName(Optional.ofNullable(
                        classStudentMapper.countManagedByClassId(session.getClassId())).orElse(0) == 0)
                .build();
    }

    public ClassroomSession findActiveByClassId(Long classId) {
        return sessionMapper.findActiveByClassId(classId);
    }

    private String generateCode() {
        for (int i = 0; i < 10; i++) {
            StringBuilder sb = new StringBuilder(6);
            for (int j = 0; j < 6; j++) sb.append(CHARS.charAt(RNG.nextInt(CHARS.length())));
            String code = sb.toString();
            if (sessionMapper.findByCode(code) == null) return code;
        }
        throw new BusinessException(ErrorCode.SYSTEM_ERROR.getCode(), "生成加入码失败");
    }
}
