package com.firedemo.demo.live.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.*;

/**
 * 举手队列服务 — 按举手时间 FIFO 排队，教师按序或指定点名。
 * 队列数据存内存（课堂级别，结课时清空）。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class HandRaiseService {

    private final SimpMessagingTemplate messagingTemplate;

    /** sessionId → 队列状态 */
    private final Map<Long, HandRaiseQueue> queues = new ConcurrentHashMap<>();

    /** 学生举手 */
    public void raise(Long sessionId, String studentId, String studentName) {
        HandRaiseQueue q = queues.computeIfAbsent(sessionId, k -> new HandRaiseQueue());
        if (q.hasStudent(studentId)) {
            log.debug("学生已在举手列表中，忽略: sessionId={}, student={}", sessionId, studentId);
            return;
        }
        q.waiting.add(new HandRaiseEntry(studentId, studentName, System.currentTimeMillis()));
        broadcast(sessionId, q);
        log.debug("学生举手: sessionId={}, student={}", sessionId, studentName);
    }

    /** 学生取消举手 */
    public void lower(Long sessionId, String studentId) {
        HandRaiseQueue q = queues.get(sessionId);
        if (q == null) return;
        q.waiting.removeIf(e -> e.studentId.equals(studentId));
        q.called.removeIf(e -> e.studentId.equals(studentId));
        broadcast(sessionId, q);
        log.debug("学生取消举手: sessionId={}, studentId={}", sessionId, studentId);
    }

    /**
     * 教师点名。
     * @param studentId 指定学生（null 则 FIFO 队首出队）
     * @return 被点名的学生，队列为空时返回 null
     */
    public HandRaiseEntry call(Long sessionId, String studentId) {
        HandRaiseQueue q = queues.get(sessionId);
        if (q == null) return null;
        HandRaiseEntry target;
        if (studentId != null && !studentId.isEmpty()) {
            target = q.waiting.stream()
                    .filter(e -> e.studentId.equals(studentId))
                    .findFirst().orElse(null);
            if (target != null) q.waiting.remove(target);
        } else {
            target = q.waiting.poll();
        }
        if (target != null) {
            q.called.add(target);
            broadcast(sessionId, q);
            log.debug("教师点名: sessionId={}, student={}", sessionId, target.studentName);
        }
        return target;
    }

    /** 教师从队列中移除学生 */
    public void dismiss(Long sessionId, String studentId) {
        HandRaiseQueue q = queues.get(sessionId);
        if (q == null) return;
        q.waiting.removeIf(e -> e.studentId.equals(studentId));
        q.called.removeIf(e -> e.studentId.equals(studentId));
        broadcast(sessionId, q);
        log.debug("教师移除举手: sessionId={}, studentId={}", sessionId, studentId);
    }

    /** 清空队列（课堂结束时调用） */
    public void clear(Long sessionId) {
        queues.remove(sessionId);
        messagingTemplate.convertAndSend("/topic/session/" + sessionId + "/hand-queue",
                (Object) Map.of("waiting", List.of(), "called", List.of()));
        log.debug("举手队列已清空: sessionId={}", sessionId);
    }

    private void broadcast(Long sessionId, HandRaiseQueue q) {
        messagingTemplate.convertAndSend("/topic/session/" + sessionId + "/hand-queue",
                (Object) Map.of("waiting", q.waiting, "called", q.called));
    }

    // ==================== 内部数据结构 ====================

    /** 举手条目 */
    public record HandRaiseEntry(String studentId, String studentName, long raisedAt) {}

    /** 单课堂的举手队列 */
    static class HandRaiseQueue {
        final Queue<HandRaiseEntry> waiting = new ConcurrentLinkedQueue<>();
        final List<HandRaiseEntry> called = new CopyOnWriteArrayList<>();

        boolean hasStudent(String sid) {
            return waiting.stream().anyMatch(e -> e.studentId.equals(sid))
                    || called.stream().anyMatch(e -> e.studentId.equals(sid));
        }
    }
}
