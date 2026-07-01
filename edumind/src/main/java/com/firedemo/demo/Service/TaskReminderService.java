package com.firedemo.demo.Service;

import com.firedemo.demo.Entity.HomeworkTask;
import com.firedemo.demo.Service.ClassService;
import com.firedemo.demo.Service.HomeworkTaskService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 作业提醒服务
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class TaskReminderService {

    private final OneBotHttpService oneBotHttpService;
    private final HomeworkTaskService taskService;
    private final ClassService classService;

    /**
     * 发送作业截止提醒（24小时前）
     */
    public void sendDeadlineReminder24h(Long taskId) {
        sendReminder(taskId, 24);
    }

    /**
     * 发送作业截止提醒（1小时前）
     */
    public void sendDeadlineReminder1h(Long taskId) {
        sendReminder(taskId, 1);
    }

    private void sendReminder(Long taskId, int hoursBefore) {
        HomeworkTask task = taskService.getById(taskId);
        if (task == null) {
            log.warn("作业不存在: taskId={}", taskId);
            return;
        }

        // 检查作业是否已截止或已关闭
        if ("closed".equals(task.getStatus())) {
            log.info("作业已关闭，跳过提醒: taskId={}", taskId);
            return;
        }
        if (task.getDeadline() != null && LocalDateTime.now().isAfter(task.getDeadline())) {
            log.info("作业已截止，跳过提醒: taskId={}", taskId);
            return;
        }

        String groupId = classService.getQqGroupId(task.getClassId());
        if (groupId == null || groupId.isEmpty()) {
            log.warn("班级未配置QQ群号: classId={}", task.getClassId());
            return;
        }

        // 查询未提交学生
        List<Map<String, Object>> unsubmitted = classService.listUnsubmittedByTaskId(
                task.getClassId(), taskId);

        if (unsubmitted.isEmpty()) {
            log.info("所有学生已提交作业，无需提醒: taskId={}", taskId);
            // 发送全员完成的祝贺消息
            oneBotHttpService.sendGroupMessage(groupId, String.format(
                    "🎉 太棒了！作业「%s」所有同学都已提交！",
                    task.getTaskName()));
            return;
        }

        // 构建未交学生名单
        String names = unsubmitted.stream()
                .map(s -> s.get("student_name") + "(" + s.get("student_id") + ")")
                .collect(Collectors.joining("、"));

        // 群消息提醒
        String deadlineStr = task.getDeadline() != null
                ? task.getDeadline().format(DateTimeFormatter.ofPattern("MM-dd HH:mm"))
                : "未设置";

        oneBotHttpService.sendGroupMessage(groupId, String.format(
                "@全体成员\n" +
                        "⏰ 作业提醒：「%s」将于%d小时后截止（%s）\n\n" +
                        "以下%d位同学尚未提交：\n%s\n\n" +
                        "请及时提交，逾期将扣分。",
                task.getTaskName(), hoursBefore, deadlineStr, unsubmitted.size(), names));

        log.info("已发送{}小时截止提醒: taskId={}, 未交人数={}", hoursBefore, taskId, unsubmitted.size());

        // 私聊提醒（仅1小时前）
        if (hoursBefore == 1) {
            for (Map<String, Object> s : unsubmitted) {
                String qqNumber = (String) s.get("qq_number");
                String studentName = (String) s.get("student_name");
                if (qqNumber != null && !qqNumber.isEmpty()) {
                    oneBotHttpService.sendPrivateMessage(qqNumber, String.format(
                            "同学%s你好，作业「%s」将于1小时后截止，你尚未提交，请尽快完成。",
                            studentName, task.getTaskName()));
                }
            }
        }
    }

    /**
     * OpenClaw cron 触发：定期作业完成情况播报（通用，不绑定截止前X小时语义）
     */
    public void sendRecurringStatusReminder(Long taskId) {
        HomeworkTask task = taskService.getById(taskId);
        if (task == null || "closed".equals(task.getStatus())) {
            return;
        }

        String groupId = classService.getQqGroupId(task.getClassId());
        if (groupId == null || groupId.isEmpty()) {
            log.warn("班级未配置QQ群号: classId={}", task.getClassId());
            return;
        }

        List<Map<String, Object>> unsubmitted = classService.listUnsubmittedByTaskId(
                task.getClassId(), taskId);
        Integer submittedCount = classService.countSubmittedByTaskId(task.getClassId(), taskId);
        Integer totalCount = classService.countStudentsByClassId(task.getClassId());

        String deadlineStr = task.getDeadline() != null
                ? task.getDeadline().format(DateTimeFormatter.ofPattern("MM-dd HH:mm"))
                : "未设置";

        if (unsubmitted.isEmpty()) {
            oneBotHttpService.sendGroupMessage(groupId, String.format(
                    "✅ 作业「%s」全员已提交！(%d/%d)",
                    task.getTaskName(), totalCount, totalCount));
        } else {
            String names = unsubmitted.stream()
                    .map(s -> s.get("student_name") + "(" + s.get("student_id") + ")")
                    .collect(Collectors.joining("、"));

            oneBotHttpService.sendGroupMessage(groupId, String.format(
                    "@全体成员\n📋 作业完成情况：「%s」\n"
                            + "截止时间：%s\n"
                            + "已交：%d/%d  未交：%d人\n\n"
                            + "未交名单：%s\n\n"
                            + "请尽快完成提交！",
                    task.getTaskName(), deadlineStr,
                    submittedCount != null ? submittedCount : 0,
                    totalCount != null ? totalCount : 0,
                    unsubmitted.size(), names));
        }

        log.info("已发送定期播报: taskId={}, 未交={}", taskId, unsubmitted.size());
    }

    /**
     * 发送作业发布通知
     */
    public void sendTaskPublishedNotification(Long classId, String taskName, LocalDateTime deadline) {
        String groupId = classService.getQqGroupId(classId);
        if (groupId == null || groupId.isEmpty()) {
            return;
        }

        String deadlineStr = deadline != null
                ? deadline.format(DateTimeFormatter.ofPattern("MM-dd HH:mm"))
                : "未设置";

        oneBotHttpService.sendGroupMessage(groupId, String.format(
                "📚 新作业发布：「%s」\n" +
                        "截止时间：%s\n" +
                        "请同学们按时完成！",
                taskName, deadlineStr));
    }

    // ========== 定时调度 ==========

    /**
     * 注册作业截止提醒（截止前24小时和1小时各提醒一次）
     */
    public void scheduleReminders(Long taskId) {
        HomeworkTask task = taskService.getById(taskId);
        if (task == null || task.getDeadline() == null) {
            log.warn("作业不存在或未设置截止时间，无法注册提醒: taskId={}", taskId);
            return;
        }

        LocalDateTime deadline = task.getDeadline();
        LocalDateTime now = LocalDateTime.now();

        LocalDateTime remind24h = deadline.minusHours(24);
        if (remind24h.isAfter(now)) {
            scheduleDelay(taskId, remind24h, 24);
        }

        LocalDateTime remind1h = deadline.minusHours(1);
        if (remind1h.isAfter(now)) {
            scheduleDelay(taskId, remind1h, 1);
        }
    }

    private void scheduleDelay(Long taskId, LocalDateTime executeTime, int hoursBefore) {
        long delayMillis = java.time.Duration.between(LocalDateTime.now(), executeTime).toMillis();
        if (delayMillis <= 0) return;

        java.util.Timer timer = new java.util.Timer("TaskReminder-" + taskId + "-" + hoursBefore);
        timer.schedule(new java.util.TimerTask() {
            @Override
            public void run() {
                try {
                    if (hoursBefore == 24) {
                        sendDeadlineReminder24h(taskId);
                    } else if (hoursBefore == 1) {
                        sendDeadlineReminder1h(taskId);
                    }
                } catch (Exception e) {
                    log.error("提醒任务执行失败: taskId={}, hoursBefore={}", taskId, hoursBefore, e);
                } finally {
                    timer.cancel();
                }
            }
        }, delayMillis);
    }
}
