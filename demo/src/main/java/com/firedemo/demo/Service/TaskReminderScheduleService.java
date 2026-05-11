package com.firedemo.demo.Service;

import com.firedemo.demo.Entity.HomeworkTask;
import com.firedemo.demo.mapper.HomeworkTaskMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Date;

/**
 * 作业提醒定时任务调度服务
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class TaskReminderScheduleService {

    private final HomeworkTaskMapper taskMapper;
    private final TaskReminderService taskReminderService;

    /**
     * 注册作业截止提醒任务
     * 在截止前24小时和1小时各提醒一次
     */
    public void scheduleReminderTasks(Long taskId) {
        HomeworkTask task = taskMapper.selectById(taskId);
        if (task == null || task.getDeadline() == null) {
            log.warn("作业不存在或未设置截止时间，无法注册提醒: taskId={}", taskId);
            return;
        }

        LocalDateTime deadline = task.getDeadline();
        LocalDateTime now = LocalDateTime.now();

        // 24小时前提醒
        LocalDateTime remind24h = deadline.minusHours(24);
        if (remind24h.isAfter(now)) {
            scheduleTask(taskId, remind24h, 24);
            log.info("已注册24小时提醒: taskId={}, time={}", taskId, remind24h);
        }

        // 1小时前提醒
        LocalDateTime remind1h = deadline.minusHours(1);
        if (remind1h.isAfter(now)) {
            scheduleTask(taskId, remind1h, 1);
            log.info("已注册1小时提醒: taskId={}, time={}", taskId, remind1h);
        }
    }

    /**
     * 调度单个提醒任务
     * 这里使用简单的延迟执行，实际生产环境建议使用 quartz 或 spring scheduler
     */
    private void scheduleTask(Long taskId, LocalDateTime executeTime, int hoursBefore) {
        // 计算延迟毫秒数
        long delayMillis = java.time.Duration.between(LocalDateTime.now(), executeTime).toMillis();
        if (delayMillis <= 0) {
            return;
        }

        // 使用定时器延迟执行（简单实现）
        java.util.Timer timer = new java.util.Timer("TaskReminder-" + taskId + "-" + hoursBefore);
        timer.schedule(new java.util.TimerTask() {
            @Override
            public void run() {
                try {
                    if (hoursBefore == 24) {
                        taskReminderService.sendDeadlineReminder24h(taskId);
                    } else if (hoursBefore == 1) {
                        taskReminderService.sendDeadlineReminder1h(taskId);
                    }
                } catch (Exception e) {
                    log.error("提醒任务执行失败: taskId={}, hoursBefore={}", taskId, hoursBefore, e);
                } finally {
                    timer.cancel();
                }
            }
        }, delayMillis);
    }

    /**
     * 取消作业的提醒任务（编辑截止时间时调用）
     */
    public void rescheduleReminderTasks(Long taskId) {
        // 简单实现：无法取消已注册的Timer任务
        // 实际生产环境建议使用可管理的调度框架（如 Quartz）
        log.info("重新注册提醒任务: taskId={}", taskId);
        scheduleReminderTasks(taskId);
    }
}
