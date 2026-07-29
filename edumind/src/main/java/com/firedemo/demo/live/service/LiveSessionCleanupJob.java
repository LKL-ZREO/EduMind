package com.firedemo.demo.live.service;

import com.firedemo.demo.Entity.ClassroomSession;
import com.firedemo.demo.mapper.ClassroomSessionMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

/** 自动结束教师持续离线的课堂，离线时间保存在数据库中以支持服务重启恢复。 */
@Slf4j
@Component
@RequiredArgsConstructor
public class LiveSessionCleanupJob {

    static final long TEACHER_OFFLINE_TIMEOUT_HOURS = 1;

    private final ClassroomSessionMapper sessionMapper;
    private final LiveSessionService sessionService;

    @Scheduled(
            initialDelayString = "${live.session.cleanup-initial-delay-ms:60000}",
            fixedDelayString = "${live.session.cleanup-interval-ms:60000}")
    public void endTeacherOfflineSessions() {
        LocalDateTime cutoff = LocalDateTime.now().minusHours(TEACHER_OFFLINE_TIMEOUT_HOURS);
        List<ClassroomSession> expired = sessionMapper.findOfflineBefore(cutoff);
        for (ClassroomSession session : expired) {
            try {
                sessionService.autoEndSession(session.getId());
            } catch (RuntimeException e) {
                log.error("自动结束离线课堂失败: sessionId={}", session.getId(), e);
            }
        }
    }
}
