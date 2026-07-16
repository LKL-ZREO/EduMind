package com.firedemo.demo.live.service;

import com.firedemo.demo.Entity.*;
import com.firedemo.demo.Service.OneBotHttpService;
import com.firedemo.demo.mapper.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 课堂直播 → OneBot QQ 通知服务
 * <p>
 * 负责将课堂事件（开课、互动关闭、结课）通过 QQ 私聊/群消息推送给学生。
 * 所有 QQ 发送都是异步的（OneBotHttpService 内部用 WebClient subscribe），不阻塞主流程。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class LiveNotificationService {

    private final OneBotHttpService oneBotHttpService;
    private final StudentQqBindingMapper qqBindingMapper;
    private final ClassStudentMapper classStudentMapper;
    private final ClassInfoMapper classInfoMapper;
    private final ClassroomSessionMapper sessionMapper;
    private final InteractionMapper interactionMapper;
    private final InteractionResponseMapper responseMapper;
    private final LiveConfusionEventMapper confusionEventMapper;
    private final StudentPresenceService presenceService;

    // ==================== 1. 未答题 QQ 提醒 ====================

    /**
     * 互动关闭时，对未作答的学生发送 QQ 私聊提醒，同时群内发汇总。
     */
    public void notifyUnanswered(Interaction interaction, ClassroomSession session) {
        List<InteractionResponse> responses = responseMapper.findByInteractionId(interaction.getId());
        Set<String> respondedIds = responses.stream()
                .map(InteractionResponse::getStudentId)
                .collect(Collectors.toSet());

        List<ClassStudent> allStudents = classStudentMapper.selectByClassId(session.getClassId());
        if (allStudents.isEmpty()) return;

        List<ClassStudent> unresponded = allStudents.stream()
                .filter(cs -> !respondedIds.contains(cs.getStudentId()))
                .toList();

        if (unresponded.isEmpty()) return;

        // 逐个私聊提醒
        int sent = 0;
        for (ClassStudent cs : unresponded) {
            String qq = qqBindingMapper.selectQqByStudentId(cs.getStudentId());
            if (qq != null && !qq.isEmpty()) {
                oneBotHttpService.sendPrivateMessage(qq, String.format(
                        "同学%s你好，课堂互动「%s」已结束，你还没有作答。下次记得积极参与哦！",
                        cs.getStudentName(), interaction.getTitle()));
                sent++;
            }
        }
        log.info("未答题提醒已发送: interactionId={}, 未答{}人, QQ私聊{}人",
                interaction.getId(), unresponded.size(), sent);

        // 群内汇总
        String groupId = classInfoMapper.selectQqGroupIdById(session.getClassId());
        if (groupId != null && !groupId.isEmpty()) {
            List<String> names = unresponded.stream()
                    .limit(10)
                    .map(cs -> cs.getStudentName() + "(" + cs.getStudentId() + ")")
                    .toList();
            String suffix = unresponded.size() > 10
                    ? String.format("等%d人", unresponded.size()) : "";
            oneBotHttpService.sendGroupMessage(groupId, String.format(
                    "⏰ 互动「%s」已结束，%d位同学未作答：%s%s",
                    interaction.getTitle(), unresponded.size(),
                    String.join("、", names), suffix));
        }
    }

    // ==================== 2. 课堂结束总结 ====================

    /**
     * 课堂结束时：群内发总结（含薄弱知识点） + 个性化复习私聊。
     */
    public void notifySessionSummary(ClassroomSession session) {
        String groupId = classInfoMapper.selectQqGroupIdById(session.getClassId());
        Long sessionId = session.getId();

        List<Interaction> interactions = interactionMapper.findBySessionId(sessionId);
        int totalInteractions = interactions.size();

        // 计算平均正确率
        double avgCorrectRate = 0;
        int countWithCorrect = 0;
        for (Interaction i : interactions) {
            if (i.getCorrectKey() == null) continue;
            List<InteractionResponse> respList = responseMapper.findByInteractionId(i.getId());
            if (respList.isEmpty()) continue;
            long correct = respList.stream().filter(r -> Boolean.TRUE.equals(r.getIsCorrect())).count();
            avgCorrectRate += (double) correct / respList.size() * 100;
            countWithCorrect++;
        }
        if (countWithCorrect > 0) avgCorrectRate /= countWithCorrect;

        var online = presenceService.getOnlineStudents(sessionId);
        var absent = presenceService.getAbsentStudents(sessionId);

        // 不懂标记统计
        List<Map<String, Object>> confusionStats = confusionEventMapper.countByKnowledgePoint(sessionId);
        String topConfusion = null;
        if (!confusionStats.isEmpty()) {
            var top = confusionStats.get(0);
            topConfusion = top.get("name") + " (" + top.get("count") + "人)";
        }

        // 找答题最活跃的学生
        Map<String, Long> answerCounts = interactions.stream()
                .flatMap(i -> responseMapper.findByInteractionId(i.getId()).stream())
                .collect(Collectors.groupingBy(InteractionResponse::getStudentName, Collectors.counting()));
        String topStudent = answerCounts.entrySet().stream()
                .max(Map.Entry.comparingByValue())
                .map(e -> e.getKey() + "(" + e.getValue() + "/" + totalInteractions + ")")
                .orElse("无");

        // ============ 群消息总结 ============
        if (groupId != null && !groupId.isEmpty()) {
            StringBuilder sb = new StringBuilder();
            sb.append(String.format("📊 课堂「%s」已结束\n\n", session.getTitle()));
            sb.append(String.format("✅ 互动: %d 次 | 👥 在线: %d 人", totalInteractions, online.size()));
            if (!absent.isEmpty()) {
                sb.append(String.format(" | 🚫 缺席: %d 人", absent.size()));
            }
            if (countWithCorrect > 0) {
                sb.append(String.format("\n🎯 平均正确率: %.0f%%", avgCorrectRate));
            }
            if (topConfusion != null) {
                sb.append(String.format("\n⚠️ 薄弱知识点: %s", topConfusion));
                if (confusionStats.size() > 1) {
                    String more = confusionStats.stream().skip(1).limit(2)
                            .map(m -> m.get("name").toString())
                            .collect(Collectors.joining("、"));
                    sb.append(String.format("、%s", more));
                }
            }
            if (!"无".equals(topStudent)) {
                sb.append(String.format("\n🏆 答题最活跃: %s", topStudent));
            }
            if (!absent.isEmpty()) {
                String absentNames = absent.stream().limit(5)
                        .map(a -> a.get("studentName"))
                        .collect(Collectors.joining("、"));
                String suffix = absent.size() > 5 ? "等" + absent.size() + "人" : "";
                sb.append(String.format("\n\n缺席: %s%s", absentNames, suffix));
            }
            sb.append("\n\n每个人将收到个性化复习包，请注意查收私聊 📩");
            oneBotHttpService.sendGroupMessage(groupId, sb.toString());
        }

        // ============ 个性化复习私聊 ============
        sendPersonalizedReview(session, interactions, confusionStats);

        // 私聊缺席学生
        int sent = 0;
        for (var a : absent) {
            String qq = qqBindingMapper.selectQqByStudentId(a.get("studentId"));
            if (qq != null && !qq.isEmpty()) {
                oneBotHttpService.sendPrivateMessage(qq, String.format(
                        "同学%s你好，你错过了今天的课堂「%s」。课堂进行了%d次互动练习，建议课后找老师补上。",
                        a.get("studentName"), session.getTitle(), totalInteractions));
                sent++;
            }
        }
        log.info("课堂结束通知已发送: sessionId={}, 互动{}次, 缺席{}人, 不懂{}种, QQ私聊{}人",
                sessionId, totalInteractions, absent.size(), confusionStats.size(), sent);
    }

    // ==================== 个性化复习包 ====================

    /**
     * 给每个参与课堂的学生发送个性化复习私聊。
     */
    private void sendPersonalizedReview(ClassroomSession session, List<Interaction> interactions,
                                         List<Map<String, Object>> confusionStats) {
        List<ClassStudent> students = classStudentMapper.selectByClassId(session.getClassId());
        if (students.isEmpty()) return;

        // 收集所有作答记录，按 studentId 分组
        Map<String, List<InteractionResponse>> responsesByStudent = interactions.stream()
                .flatMap(i -> responseMapper.findByInteractionId(i.getId()).stream())
                .collect(Collectors.groupingBy(InteractionResponse::getStudentId));

        // 不懂标记按学生分组
        List<LiveConfusionEvent> allConfusions = confusionEventMapper.findBySessionId(session.getId());
        Map<String, List<LiveConfusionEvent>> confusionsByStudent = allConfusions.stream()
                .collect(Collectors.groupingBy(LiveConfusionEvent::getStudentId));

        int sent = 0;
        for (ClassStudent cs : students) {
            String qq = qqBindingMapper.selectQqByStudentId(cs.getStudentId());
            if (qq == null || qq.isEmpty()) continue;

            String msg = buildReviewMessage(session.getTitle(), cs,
                    responsesByStudent.getOrDefault(cs.getStudentId(), List.of()),
                    confusionsByStudent.getOrDefault(cs.getStudentId(), List.of()),
                    interactions);
            oneBotHttpService.sendPrivateMessage(qq, msg);
            sent++;
        }
        log.info("个性化复习包已发送: sessionId={}, 发送{}人", session.getId(), sent);
    }

    private String buildReviewMessage(String title, ClassStudent cs,
                                       List<InteractionResponse> myResponses,
                                       List<LiveConfusionEvent> myConfusions,
                                       List<Interaction> allInteractions) {
        int total = allInteractions.size();
        int answered = myResponses.size();
        long correct = myResponses.stream().filter(r -> Boolean.TRUE.equals(r.getIsCorrect())).count();

        // 掌握的知识点（答对的题对应的知识点）
        Set<String> interactionIdsCorrect = myResponses.stream()
                .filter(r -> Boolean.TRUE.equals(r.getIsCorrect()))
                .map(r -> r.getInteractionId().toString())
                .collect(Collectors.toSet());
        Set<String> masteredKps = allInteractions.stream()
                .filter(i -> interactionIdsCorrect.contains(i.getId().toString()) && i.getKnowledgePoint() != null)
                .map(Interaction::getKnowledgePoint)
                .filter(kp -> !kp.isEmpty())
                .collect(Collectors.toSet());

        // 薄弱知识点（答错 + 标记不懂）
        Set<String> weakFromWrong = myResponses.stream()
                .filter(r -> !Boolean.TRUE.equals(r.getIsCorrect()) && r.getInteractionId() != null)
                .map(r -> {
                    Interaction inter = allInteractions.stream()
                            .filter(i -> i.getId().equals(r.getInteractionId()))
                            .findFirst().orElse(null);
                    return inter != null ? inter.getKnowledgePoint() : null;
                })
                .filter(kp -> kp != null && !kp.isEmpty())
                .collect(Collectors.toSet());
        Set<String> weakFromConfusion = myConfusions.stream()
                .map(LiveConfusionEvent::getKnowledgePoint)
                .filter(kp -> kp != null && !kp.isEmpty())
                .collect(Collectors.toSet());
        Set<String> allWeak = new java.util.LinkedHashSet<>(weakFromWrong);
        allWeak.addAll(weakFromConfusion);
        // 去重 mastered
        allWeak.removeAll(masteredKps);

        StringBuilder sb = new StringBuilder();
        sb.append(String.format("📝 今日课堂复习包「%s」\n\n", title));
        sb.append(String.format("👤 %s (%s)\n", cs.getStudentName(), cs.getStudentId()));
        sb.append(String.format("📋 参与: %d/%d 题  |  ✅ 正确: %d 题\n\n", answered, total, correct));

        if (!masteredKps.isEmpty()) {
            sb.append("✅ 你掌握了:\n");
            masteredKps.forEach(kp -> sb.append("  · ").append(kp).append("\n"));
            sb.append("\n");
        }

        if (!allWeak.isEmpty()) {
            sb.append("⚠️ 需要加强:\n");
            allWeak.forEach(kp -> sb.append("  · ").append(kp).append("\n"));
            sb.append("\n💡 建议课后复习这些知识点，下次课老师也会重点讲解。\n");
        } else if (!masteredKps.isEmpty()) {
            sb.append("🎉 太棒了，所有知识点都掌握得很好！\n");
        }

        if (myConfusions.size() > 0) {
            sb.append("\n🤔 你在课堂上标记了不懂的内容:\n");
            myConfusions.forEach(c -> {
                if (c.getKnowledgePoint() != null) {
                    sb.append("  · ").append(c.getKnowledgePoint()).append("\n");
                }
            });
        }

        return sb.toString();
    }

    // ==================== 3. 开课群通知 ====================

    /**
     * 老师创建课堂时，群内发开课通知（含加入码）。
     */
    public void notifySessionStart(ClassroomSession session) {
        String groupId = classInfoMapper.selectQqGroupIdById(session.getClassId());
        if (groupId == null || groupId.isEmpty()) {
            log.debug("班级未配置QQ群号，跳过开课通知: classId={}", session.getClassId());
            return;
        }

        ClassInfo classInfo = classInfoMapper.selectById(session.getClassId());
        String className = classInfo != null ? classInfo.getName() : "课堂";

        oneBotHttpService.sendGroupMessage(groupId, String.format(
                "📚 老师已开启「%s」课堂\n\n" +
                "🏷 课堂码：%s\n" +
                "📝 课程：%s\n\n" +
                "请在浏览器中打开链接，输入学号和姓名加入课堂。",
                session.getTitle(), session.getSessionCode(), className));

        log.info("开课通知已发送: sessionId={}, code={}, groupId={}",
                session.getId(), session.getSessionCode(), groupId);
    }

    // ==================== 4. 预习任务推送 ====================

    /**
     * 教师发布预习任务时，群内推送提醒。
     */
    public void notifyPreviewTaskPublished(PreviewTask task, ClassInfo classInfo) {
        String groupId = classInfoMapper.selectQqGroupIdById(task.getClassId());
        if (groupId == null || groupId.isEmpty()) {
            log.debug("班级未配置QQ群号，跳过预习任务推送: classId={}", task.getClassId());
            return;
        }

        String className = classInfo != null ? classInfo.getName() : "班级";
        oneBotHttpService.sendGroupMessage(groupId, String.format(
                "📖 老师发布了新的预习任务\n\n" +
                "📝 主题：%s\n" +
                "📚 知识点：%s\n" +
                "🏫 班级：%s\n\n" +
                "请在课前完成预习，带着问题来上课！",
                task.getTitle(), task.getKnowledgePoint(), className));

        log.info("预习任务推送已发送: taskId={}, groupId={}", task.getId(), groupId);
    }
}
