package com.firedemo.demo.live.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.firedemo.demo.DTO.*;
import com.firedemo.demo.Entity.*;
import com.firedemo.demo.Service.OpenClawService;
import com.firedemo.demo.common.exception.BusinessException;
import com.firedemo.demo.common.exception.ErrorCode;
import com.firedemo.demo.mapper.*;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class InteractionService {

    private final InteractionMapper interactionMapper;
    private final InteractionResponseMapper responseMapper;
    private final ClassStudentMapper classStudentMapper;
    private final ClassroomSessionMapper sessionMapper;
    private final SimpMessagingTemplate messagingTemplate;
    private final ObjectMapper objectMapper;
    private final OpenClawService openClawService;
    private final LiveNotificationService liveNotificationService;

    /** 自动关闭互动的定时任务执行器（2 个调度线程足够，实际关闭逻辑在调用线程执行） */
    private static final ScheduledExecutorService autoCloseScheduler =
            Executors.newScheduledThreadPool(2, Thread.ofPlatform().name("interaction-auto-close-", 0).factory());

    /** 待执行的自动关闭任务，用于手动关闭时取消 */
    private final Map<Long, ScheduledFuture<?>> pendingClosures = new ConcurrentHashMap<>();

    @PreDestroy
    public void shutdown() {
        log.info("关闭自动关闭调度器，取消 {} 个待执行任务", pendingClosures.size());
        pendingClosures.values().forEach(f -> f.cancel(false));
        pendingClosures.clear();
        autoCloseScheduler.shutdownNow();
    }

    @Transactional
    public Interaction createAndActivate(Long sessionId, Long teacherId, InteractionCreateDTO dto) {
        String optionsJson = null;
        if (dto.getOptions() != null && !dto.getOptions().isEmpty()) {
            try { optionsJson = objectMapper.writeValueAsString(dto.getOptions()); }
            catch (JsonProcessingException e) { throw new BusinessException(ErrorCode.PARAM_ERROR.getCode(), "选项格式错误"); }
        }

        Interaction interaction = Interaction.builder()
                .sessionId(sessionId).type(dto.getType()).title(dto.getTitle())
                .description(dto.getDescription()).options(optionsJson)
                .correctKey(dto.getCorrectKey()).timeLimit(dto.getTimeLimit())
                .status("ACTIVE").sortOrder(0).aiGenerated(false)
                .knowledgePoint(dto.getKnowledgePoint()).build();
        interactionMapper.insertWithJsonb(interaction);

        InteractionPushDTO push = buildPushDTO(interaction);
        messagingTemplate.convertAndSend("/topic/session/" + sessionId + "/interaction", push);

        // 创建互动后立即推送初始统计（0回答），老师端不用等学生作答
        ClassroomSession session = sessionMapper.selectById(sessionId);
        if (session != null) pushStatsToTeacher(interaction, session.getClassId(), session.getTeacherId());

        if (dto.getTimeLimit() != null && dto.getTimeLimit() > 0) {
            scheduleAutoClose(interaction.getId(), sessionId, dto.getTimeLimit());
        }

        log.info("互动已激活: interactionId={}, type={}, sessionId={}", interaction.getId(), dto.getType(), sessionId);
        return interaction;
    }

    public void handleResponse(Long sessionId, StudentResponseDTO dto) {
        Interaction interaction = interactionMapper.selectById(dto.getInteractionId());
        if (interaction == null || !"ACTIVE".equals(interaction.getStatus()))
            throw new BusinessException(ErrorCode.PARAM_ERROR.getCode(), "互动已关闭或不存在");

        Boolean isCorrect = null;
        if ("CHOICE".equals(interaction.getType()) && interaction.getCorrectKey() != null)
            isCorrect = interaction.getCorrectKey().equalsIgnoreCase(dto.getAnswer());

        InteractionResponse response = InteractionResponse.builder()
                .interactionId(dto.getInteractionId()).sessionId(sessionId)
                .studentId(dto.getStudentId()).studentName(dto.getStudentName())
                .answer(dto.getAnswer()).isCorrect(isCorrect)
                .score(isCorrect != null && isCorrect ? 1 : 0)
                .respondedAt(LocalDateTime.now()).build();
        responseMapper.upsert(response);

        ClassroomSession session = sessionMapper.selectById(sessionId);
        if (session != null) pushStatsToTeacher(interaction, session.getClassId(), session.getTeacherId());
    }

    @Transactional
    public void closeInteraction(Long interactionId, Long sessionId) {
        // 取消自动关闭定时任务（如果教师手动提前关闭）
        ScheduledFuture<?> future = pendingClosures.remove(interactionId);
        if (future != null) {
            future.cancel(false);
        }

        Interaction interaction = interactionMapper.selectById(interactionId);
        if (interaction == null || !"ACTIVE".equals(interaction.getStatus())) return;
        interactionMapper.closeInteraction(interactionId);

        InteractionPushDTO push = buildPushDTO(interaction);
        push.setStatus("CLOSED");
        messagingTemplate.convertAndSend("/topic/session/" + sessionId + "/interaction", push);

        ClassroomSession session = sessionMapper.selectById(sessionId);
        if (session != null) {
            pushStatsToTeacher(interaction, session.getClassId(), session.getTeacherId());
            // OneBot QQ 提醒未答题学生
            liveNotificationService.notifyUnanswered(interaction, session);
        }
        log.info("互动已关闭: interactionId={}", interactionId);
    }

    public void pushStatsToTeacher(Interaction interaction, Long classId, Long teacherId) {
        LiveStatsDTO stats = buildStats(interaction, classId);
        log.info("推送统计: teacherId={}, interactionId={}, responded={}/{}",
                teacherId, interaction.getId(), stats.getRespondedCount(), stats.getTotalStudents());
        // 用 topic 广播而非用户队列，避免 convertAndSendToUser 的 principal 匹配问题
        messagingTemplate.convertAndSend("/topic/session/" + interaction.getSessionId() + "/stats", stats);
    }

    private LiveStatsDTO buildStats(Interaction interaction, Long classId) {
        int totalStudents = Optional.ofNullable(classStudentMapper.countByClassId(classId)).orElse(0);
        List<InteractionResponse> responses = responseMapper.findByInteractionId(interaction.getId());
        int respondedCount = responses.size();

        Map<String, Long> rawDist = responses.stream()
                .collect(Collectors.groupingBy(r -> r.getAnswer() != null ? r.getAnswer() : "未作答", Collectors.counting()));
        Map<String, LiveStatsDTO.DistributionItem> distribution = new LinkedHashMap<>();
        rawDist.forEach((key, count) -> distribution.put(key, LiveStatsDTO.DistributionItem.builder()
                .count(count.intValue())
                .percent(respondedCount > 0 ? Math.round(count * 1000.0 / respondedCount) / 10.0 : 0).build()));

        Double correctRate = null;
        if (interaction.getCorrectKey() != null) {
            long correctCount = responses.stream().filter(r -> Boolean.TRUE.equals(r.getIsCorrect())).count();
            correctRate = respondedCount > 0 ? Math.round(correctCount * 1000.0 / respondedCount) / 10.0 : 0.0;
        }

        Set<String> respondedIds = responses.stream().map(InteractionResponse::getStudentId).collect(Collectors.toSet());
        List<String> unresponded = classId != null ? classStudentMapper.selectByClassId(classId).stream()
                .map(cs -> cs.getStudentId()).filter(sid -> !respondedIds.contains(sid)).limit(10).toList() : List.of();

        return LiveStatsDTO.builder()
                .interactionId(interaction.getId()).status(interaction.getStatus())
                .totalStudents(totalStudents).respondedCount(respondedCount)
                .distribution(distribution).correctRate(correctRate).unrespondedStudents(unresponded).build();
    }

    public InteractionPushDTO buildPushDTO(Interaction interaction) {
        List<InteractionPushDTO.OptionItem> options = null;
        if (interaction.getOptions() != null && !interaction.getOptions().isEmpty()) {
            try {
                List<InteractionCreateDTO.OptionDTO> raw = objectMapper.readValue(interaction.getOptions(),
                        objectMapper.getTypeFactory().constructCollectionType(List.class, InteractionCreateDTO.OptionDTO.class));
                options = raw.stream().map(o -> InteractionPushDTO.OptionItem.builder().key(o.getKey()).text(o.getText()).build()).toList();
            } catch (JsonProcessingException ignored) {}
        }
        return InteractionPushDTO.builder()
                .interactionId(interaction.getId()).type(interaction.getType())
                .status("ACTIVE".equals(interaction.getStatus()) ? "ACTIVE" : "CLOSED")
                .title(interaction.getTitle()).description(interaction.getDescription())
                .correctKey(interaction.getCorrectKey())
                .options(options).timeLimit(interaction.getTimeLimit())
                .deadlineEpochMs(interaction.getTimeLimit() != null && interaction.getTimeLimit() > 0
                        ? System.currentTimeMillis() + interaction.getTimeLimit() * 1000L : null)
                .serverTime(LocalDateTime.now().toString()).build();
    }

    public InteractionPushDTO getActiveInteraction(Long sessionId) {
        Interaction active = interactionMapper.findActiveBySessionId(sessionId);
        return active != null ? buildPushDTO(active) : null;
    }

    /** 获取当前活跃互动的统计（老师刷新页面兜底） */
    public LiveStatsDTO getActiveInteractionStats(Long sessionId) {
        Interaction active = interactionMapper.findActiveBySessionId(sessionId);
        if (active == null) return null;
        ClassroomSession session = sessionMapper.selectById(sessionId);
        if (session == null) return null;
        return buildStats(active, session.getClassId());
    }

    /** 获取单个互动详情（含分布 + 学生作答明细） */
    public InteractionDetailDTO getInteractionDetail(Long sessionId, Long interactionId) {
        Interaction interaction = interactionMapper.selectById(interactionId);
        if (interaction == null) return null;
        ClassroomSession session = sessionMapper.selectById(sessionId);
        Long classId = session != null ? session.getClassId() : null;
        int totalStudents = classId != null ? Optional.ofNullable(classStudentMapper.countByClassId(classId)).orElse(0) : 0;

        List<InteractionResponse> responses = responseMapper.findByInteractionId(interactionId);
        int respondedCount = responses.size();

        // 分布
        Map<String, Long> rawDist = responses.stream()
                .collect(Collectors.groupingBy(r -> r.getAnswer() != null ? r.getAnswer() : "未作答", Collectors.counting()));
        Map<String, InteractionDetailDTO.DistributionItem> distribution = new LinkedHashMap<>();
        rawDist.forEach((key, count) -> distribution.put(key, InteractionDetailDTO.DistributionItem.builder()
                .count(count.intValue())
                .percent(respondedCount > 0 ? Math.round(count * 1000.0 / respondedCount) / 10.0 : 0).build()));

        Double correctRate = null;
        if (interaction.getCorrectKey() != null && respondedCount > 0) {
            long correctCount = responses.stream().filter(r -> Boolean.TRUE.equals(r.getIsCorrect())).count();
            correctRate = Math.round(correctCount * 1000.0 / respondedCount) / 10.0;
        }

        Set<String> respondedIds = responses.stream().map(InteractionResponse::getStudentId).collect(Collectors.toSet());
        List<String> unresponded = classId != null ? classStudentMapper.selectByClassId(classId).stream()
                .map(cs -> cs.getStudentId()).filter(sid -> !respondedIds.contains(sid)).limit(20).toList() : List.of();

        List<InteractionDetailDTO.ResponseItem> responseItems = responses.stream()
                .map(r -> InteractionDetailDTO.ResponseItem.builder()
                        .studentId(r.getStudentId()).studentName(r.getStudentName())
                        .answer(r.getAnswer()).isCorrect(r.getIsCorrect()).respondedAt(r.getRespondedAt()).build())
                .toList();

        List<InteractionCreateDTO.OptionDTO> options = null;
        if (interaction.getOptions() != null && !interaction.getOptions().isEmpty()) {
            try {
                options = objectMapper.readValue(interaction.getOptions(),
                        objectMapper.getTypeFactory().constructCollectionType(List.class, InteractionCreateDTO.OptionDTO.class));
            } catch (JsonProcessingException ignored) {}
        }

        return InteractionDetailDTO.builder()
                .interactionId(interaction.getId()).type(interaction.getType()).title(interaction.getTitle())
                .description(interaction.getDescription()).options(options).correctKey(interaction.getCorrectKey())
                .timeLimit(interaction.getTimeLimit()).status(interaction.getStatus())
                .totalStudents(totalStudents).respondedCount(respondedCount)
                .distribution(distribution).correctRate(correctRate).unrespondedStudents(unresponded)
                .responses(responseItems).build();
    }

    /** 获取全部互动历史（教师端看统计，学生端看自己作答）。一次批量查询所有 response，避免 N+1。 */
    public List<InteractionHistoryDTO> getInteractionHistory(Long sessionId, String studentId) {
        List<Interaction> interactions = interactionMapper.findBySessionId(sessionId);
        if (interactions.isEmpty()) return List.of();

        ClassroomSession session = sessionMapper.selectById(sessionId);
        Long classId = session != null ? session.getClassId() : null;
        int totalStudents = classId != null ? Optional.ofNullable(classStudentMapper.countByClassId(classId)).orElse(0) : 0;

        // 一次查询拉取该 session 下所有 response，按 interactionId 分组
        List<InteractionResponse> allResponses = responseMapper.findBySessionId(sessionId);
        Map<Long, List<InteractionResponse>> responsesByInteraction = allResponses.stream()
                .collect(Collectors.groupingBy(InteractionResponse::getInteractionId));

        return interactions.stream().map(i -> {
            List<InteractionResponse> responses = responsesByInteraction.getOrDefault(i.getId(), List.of());
            int respondedCount = responses.size();

            Double correctRate = null;
            if (i.getCorrectKey() != null && respondedCount > 0) {
                long correctCount = responses.stream().filter(r -> Boolean.TRUE.equals(r.getIsCorrect())).count();
                correctRate = Math.round(correctCount * 1000.0 / respondedCount) / 10.0;
            }

            // 学生自己的作答
            String myAnswer = null;
            Boolean myCorrect = null;
            if (studentId != null) {
                InteractionResponse myResp = responses.stream()
                        .filter(r -> studentId.equals(r.getStudentId())).findFirst().orElse(null);
                if (myResp != null) { myAnswer = myResp.getAnswer(); myCorrect = myResp.getIsCorrect(); }
            }

            // 解析选项
            List<InteractionCreateDTO.OptionDTO> options = null;
            if (i.getOptions() != null && !i.getOptions().isEmpty()) {
                try {
                    List<InteractionCreateDTO.OptionDTO> raw = objectMapper.readValue(i.getOptions(),
                            objectMapper.getTypeFactory().constructCollectionType(List.class, InteractionCreateDTO.OptionDTO.class));
                    options = raw;
                } catch (JsonProcessingException ignored) {}
            }

            return InteractionHistoryDTO.builder()
                    .interactionId(i.getId()).type(i.getType()).title(i.getTitle())
                    .description(i.getDescription()).options(options).correctKey(i.getCorrectKey())
                    .timeLimit(i.getTimeLimit()).status(i.getStatus())
                    .createdAt(i.getCreatedAt() != null ? i.getCreatedAt().toString() : null)
                    .totalStudents(totalStudents).respondedCount(respondedCount).correctRate(correctRate)
                    .myAnswer(myAnswer).myCorrect(myCorrect).build();
        }).toList();
    }

    /**
     * AI 生成题目（调用 OpenClaw 生成选择题/简答题）
     */
    public Map<String, Object> generateQuestion(String topic, String type) {
        String typeName = "CHOICE".equals(type) ? "选择题（含4个选项A/B/C/D和正确答案）" : "简答题";
        String prompt = String.format(
                "你是一位资深教师。请根据以下知识点生成一道%s。\n知识点：%s\n\n" +
                "输出严格JSON格式（不要markdown代码块）：\n" +
                "{\"title\":\"题目内容\",\"options\":[{\"key\":\"A\",\"text\":\"选项A\"},...],\"correctKey\":\"A\"}\n" +
                "如果是简答题，options字段为null，correctKey为参考答案。", typeName, topic);

        String raw = openClawService.chat(prompt, "1");
        try {
            String json = extractJson(raw);
            @SuppressWarnings("unchecked")
            Map<String, Object> result = objectMapper.readValue(json, Map.class);
            result.put("type", type);
            return result;
        } catch (Exception e) {
            log.error("AI生成题目解析失败: {}", e.getMessage());
            throw new BusinessException(ErrorCode.SYSTEM_ERROR.getCode(), "AI生成失败，请重试");
        }
    }

    private String extractJson(String raw) {
        if (raw == null) return "{}";
        String s = raw.trim();
        int start = s.indexOf('{');
        int end = s.lastIndexOf('}');
        if (start >= 0 && end > start) return s.substring(start, end + 1);
        return s;
    }

    /** 学生个人画像：汇总该学生在某班级所有课堂中的答题表现。批量查询避免 N+1。 */
    public Map<String, Object> getStudentProfile(String studentId, Long classId) {
        var sessions = sessionMapper.findByClassId(classId);
        if (sessions.isEmpty()) return buildEmptyProfile(studentId, classId, 0);

        List<Long> sessionIds = sessions.stream().map(s -> s.getId()).toList();

        // 批量查询：所有 session 的互动 + 所有 response
        List<Interaction> allInteractions = interactionMapper.findBySessionIds(sessionIds);
        List<InteractionResponse> allResponses = responseMapper.findBySessionIds(sessionIds);

        // 按 interactionId 分组 response
        Map<Long, List<InteractionResponse>> responsesByInteraction = allResponses.stream()
                .collect(Collectors.groupingBy(InteractionResponse::getInteractionId));

        int totalAnswers = 0, correctAnswers = 0;
        for (var interaction : allInteractions) {
            var responses = responsesByInteraction.getOrDefault(interaction.getId(), List.of());
            var myResp = responses.stream().filter(r -> studentId.equals(r.getStudentId())).findFirst();
            if (myResp.isPresent()) {
                totalAnswers++;
                if (Boolean.TRUE.equals(myResp.get().getIsCorrect())) correctAnswers++;
            }
        }

        int totalInteractions = allInteractions.size();
        double correctRate = totalAnswers > 0 ? Math.round(correctAnswers * 1000.0 / totalAnswers) / 10.0 : 0;
        double participationRate = totalInteractions > 0 ? Math.round(totalAnswers * 1000.0 / totalInteractions) / 10.0 : 0;

        return Map.of(
                "studentId", studentId,
                "classId", classId,
                "totalSessions", sessions.size(),
                "totalInteractions", totalInteractions,
                "totalAnswers", totalAnswers,
                "correctAnswers", correctAnswers,
                "correctRate", correctRate,
                "participationRate", participationRate);
    }

    private static Map<String, Object> buildEmptyProfile(String studentId, Long classId, int sessions) {
        return Map.of("studentId", studentId, "classId", classId, "totalSessions", sessions,
                "totalInteractions", 0, "totalAnswers", 0, "correctAnswers", 0,
                "correctRate", 0.0, "participationRate", 0.0);
    }

    private void scheduleAutoClose(Long interactionId, Long sessionId, int timeLimitSeconds) {
        ScheduledFuture<?> future = autoCloseScheduler.schedule(() -> {
            pendingClosures.remove(interactionId);
            try {
                closeInteraction(interactionId, sessionId);
            } catch (Exception e) {
                log.error("自动关闭互动失败: interactionId={}", interactionId, e);
            }
        }, timeLimitSeconds, TimeUnit.SECONDS);
        pendingClosures.put(interactionId, future);
        log.debug("已调度自动关闭: interactionId={}, 延时={}s", interactionId, timeLimitSeconds);
    }
}
