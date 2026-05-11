package com.firedemo.demo.Service;

import com.firedemo.demo.Entity.HomeworkTask;
import com.firedemo.demo.mapper.ClassInfoMapper;
import com.firedemo.demo.mapper.ClassStudentMapper;
import com.firedemo.demo.mapper.HomeworkTaskMapper;
import com.firedemo.demo.mapper.StudentQqBindingMapper;
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
    private final HomeworkTaskMapper taskMapper;
    private final ClassInfoMapper classInfoMapper;
    private final ClassStudentMapper classStudentMapper;
    private final StudentQqBindingMapper studentQqBindingMapper;

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
        HomeworkTask task = taskMapper.selectById(taskId);
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

        String groupId = classInfoMapper.selectQqGroupIdById(task.getClassId());
        if (groupId == null || groupId.isEmpty()) {
            log.warn("班级未配置QQ群号: classId={}", task.getClassId());
            return;
        }

        // 查询未提交学生
        List<Map<String, Object>> unsubmitted = classStudentMapper.selectUnsubmittedByTaskId(
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
     * 发送作业发布通知
     */
    public void sendTaskPublishedNotification(Long classId, String taskName, LocalDateTime deadline) {
        String groupId = classInfoMapper.selectQqGroupIdById(classId);
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
}
