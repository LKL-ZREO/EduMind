package com.firedemo.demo.live.service;

import com.firedemo.demo.DTO.*;
import com.firedemo.demo.Entity.*;
import com.firedemo.demo.common.exception.BusinessException;
import com.firedemo.demo.common.exception.ErrorCode;
import com.firedemo.demo.mapper.*;
import com.firedemo.demo.utils.JwtUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class LiveSessionService {

    private final ClassroomSessionMapper sessionMapper;
    private final ClassInfoMapper classInfoMapper;
    private final ClassStudentMapper classStudentMapper;
    private final InteractionMapper interactionMapper;
    private final UserMapper userMapper;
    private final JwtUtil jwtUtil;
    private final LiveNotificationService liveNotificationService;
    private final HandRaiseService handRaiseService;
    private final StudentPresenceService presenceService;

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
                .courseId(dto.getCourseId()).status("ACTIVE").startedAt(LocalDateTime.now()).build();
        sessionMapper.insert(session);
        log.info("课堂已创建: sessionId={}, code={}", session.getId(), code);
        // OneBot QQ 群开课通知
        liveNotificationService.notifySessionStart(session);
        return session;
    }

    @Transactional
    public LiveSessionInfoDTO joinSession(LiveJoinDTO dto) {
        ClassroomSession session = sessionMapper.findByCode(dto.getCode());
        if (session == null)
            throw new BusinessException(ErrorCode.DATA_NOT_FOUND.getCode(), "课堂不存在或已结束");

        // 验证学号是否在班级花名册中（如果花名册非空，则必须在名单内）
        int totalInClass = Optional.ofNullable(classStudentMapper.countByClassId(session.getClassId())).orElse(0);
        if (totalInClass > 0 && !classStudentMapper.existsByClassIdAndStudentId(session.getClassId(), dto.getStudentId())) {
            throw new BusinessException(ErrorCode.PARAM_ERROR.getCode(), "学号不在班级名单中，请联系老师添加");
        }

        classStudentMapper.insertIgnore(session.getClassId(), dto.getStudentId(), dto.getStudentName(), "manual");

        String token = jwtUtil.generateStudentToken(dto.getStudentId(), dto.getStudentName(), session.getId());
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
                .studentId(dto.getStudentId()).studentName(dto.getStudentName()).build();
    }

    @Transactional
    public void endSession(Long sessionId, Long teacherId) {
        ClassroomSession session = sessionMapper.selectById(sessionId);
        if (session == null) throw new BusinessException(ErrorCode.DATA_NOT_FOUND.getCode(), "课堂不存在");
        if (!session.getTeacherId().equals(teacherId))
            throw new BusinessException(ErrorCode.FORBIDDEN.getCode(), "无权结束此课堂");
        sessionMapper.endSession(sessionId);
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
                .teacherName(teacherName).build();
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
