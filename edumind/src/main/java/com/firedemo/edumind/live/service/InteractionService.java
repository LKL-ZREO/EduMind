package com.firedemo.edumind.live.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.firedemo.edumind.live.InteractionCreateDTO;
import com.firedemo.edumind.live.InteractionDetailDTO;
import com.firedemo.edumind.live.InteractionHistoryDTO;
import com.firedemo.edumind.live.InteractionPushDTO;
import com.firedemo.edumind.live.InteractionTimingDTO;
import com.firedemo.edumind.live.LiveStatsDTO;
import com.firedemo.edumind.live.QuestionBoardItemDTO;
import com.firedemo.edumind.live.StudentResponseDTO;
import com.firedemo.edumind.live.ClassroomSession;
import com.firedemo.edumind.live.Interaction;
import com.firedemo.edumind.live.InteractionResponse;
import com.firedemo.edumind.teaching.QuestionBankItem;
import com.firedemo.edumind.assistant.AgentService;
import com.firedemo.edumind.teaching.QuestionService;
import com.firedemo.edumind.shared.exception.BusinessException;
import com.firedemo.edumind.shared.exception.ErrorCode;
import com.firedemo.edumind.assistant.structured.StructuredOutputInvoker;
import com.firedemo.edumind.assistant.structured.StructuredOutputValidationException;
import com.firedemo.edumind.classroom.ClassStudentMapper;
import com.firedemo.edumind.live.ClassroomSessionMapper;
import com.firedemo.edumind.live.InteractionMapper;
import com.firedemo.edumind.live.InteractionResponseMapper;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneId;
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
    private final AgentService agentService;
    private final StructuredOutputInvoker structuredOutputInvoker;
    private final LiveNotificationService liveNotificationService;
    private final QuestionService questionService;

    /** 自动关闭互动的定时任务执行器（2 个调度线程足够，实际关闭逻辑在调用线程执行） */
    private final ScheduledExecutorService autoCloseScheduler =
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
        ClassroomSession session = lockActiveSession(sessionId);
        if (teacherId != null && !teacherId.equals(session.getTeacherId())) {
            throw new BusinessException(ErrorCode.FORBIDDEN.getCode(), "无权操作该课堂");
        }
        ensureNoActiveInteraction(sessionId);
        QuestionBankItem question = questionService.createFromInteraction(session.getTeacherId(), dto);
        return createActiveInteraction(session, question, dto.getTimeLimit());
    }

    public Interaction getById(Long interactionId) {
        return interactionMapper.selectById(interactionId);
    }

    /** 从统一题库发送题目，并创建一条独立的课堂发送快照。 */
    @Transactional
    public InteractionPushDTO sendQuestion(Long sessionId, Long questionId, Integer timeLimitOverride) {
        ClassroomSession session = lockActiveSession(sessionId);
        ensureNoActiveInteraction(sessionId);
        QuestionBankItem question = questionService.requireOwnedEntity(session.getTeacherId(), questionId);
        if (!Set.of("CHOICE", "OPEN", "EXERCISE").contains(question.getType())) {
            throw new BusinessException(ErrorCode.PARAM_ERROR.getCode(), "该题型不适合实时课堂");
        }
        if (timeLimitOverride != null && (timeLimitOverride < 1 || timeLimitOverride > 1800)) {
            throw new BusinessException(ErrorCode.PARAM_ERROR.getCode(), "作答时间应在1到1800秒之间");
        }
        Interaction interaction = createActiveInteraction(session, question, timeLimitOverride);
        return buildPushDTO(interaction);
    }

    private Interaction createActiveInteraction(ClassroomSession session,
                                                QuestionBankItem question,
                                                Integer timeLimitOverride) {
        Integer timeLimit = timeLimitOverride != null
                ? timeLimitOverride : question.getDefaultTimeLimit();
        LocalDateTime activatedAt = LocalDateTime.now();
        LocalDateTime deadlineAt = calculateDeadline(activatedAt, timeLimit);
        Interaction interaction = Interaction.builder()
                .questionId(question.getId())
                .sessionId(session.getId())
                .classId(session.getClassId())
                .sourceDocId(question.getSourceDocId())
                .type(question.getType())
                .title(question.getTitle())
                .description(question.getRequirement())
                .options(question.getOptions())
                .correctKey(question.getCorrectKey())
                .explanation(question.getExplanation())
                .timeLimit(timeLimit)
                .status("ACTIVE")
                .sortOrder(0)
                .aiGenerated(question.getAiGenerated())
                .knowledgePoint(question.getKnowledgePoint())
                .difficulty(question.getDifficulty())
                .activatedAt(activatedAt)
                .deadlineAt(deadlineAt)
                .build();
        interactionMapper.insertWithJsonb(interaction);

        InteractionPushDTO push = buildPushDTO(interaction);
        messagingTemplate.convertAndSend(
                "/topic/session/" + session.getId() + "/interaction", push);
        pushStatsToTeacher(interaction, session.getClassId(), session.getTeacherId());
        scheduleAutoCloseAt(interaction);
        log.info("题目已发送: questionId={}, interactionId={}, sessionId={}",
                question.getId(), interaction.getId(), session.getId());
        return interaction;
    }

    public void handleResponse(Long sessionId, StudentResponseDTO dto) {
        Interaction interaction = interactionMapper.selectById(dto.getInteractionId());
        if (interaction == null || !"ACTIVE".equals(interaction.getStatus()))
            throw new BusinessException(ErrorCode.PARAM_ERROR.getCode(), "互动已关闭或不存在");
        if (!sessionId.equals(interaction.getSessionId()))
            throw new BusinessException(ErrorCode.FORBIDDEN.getCode(), "互动不属于当前课堂");
        if (interaction.getDeadlineAt() != null && !interaction.getDeadlineAt().isAfter(LocalDateTime.now())) {
            closeInteraction(interaction.getId(), sessionId);
            throw new BusinessException(ErrorCode.PARAM_ERROR.getCode(), "作答时间已结束");
        }

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
        Interaction interaction = interactionMapper.selectById(interactionId);
        if (interaction == null || !"ACTIVE".equals(interaction.getStatus())) return;
        if (!sessionId.equals(interaction.getSessionId()))
            throw new BusinessException(ErrorCode.FORBIDDEN.getCode(), "互动不属于当前课堂");

        // 取消自动关闭定时任务（如果教师手动提前关闭）
        ScheduledFuture<?> future = pendingClosures.remove(interactionId);
        if (future != null) {
            future.cancel(false);
        }

        if (interactionMapper.closeInteraction(interactionId, sessionId) == 0) return;

        interaction.setStatus("CLOSED");
        interaction.setClosedAt(LocalDateTime.now());
        interaction.setDeadlineAt(null);
        InteractionPushDTO push = buildPushDTO(interaction);
        messagingTemplate.convertAndSend("/topic/session/" + sessionId + "/interaction", push);

        ClassroomSession session = sessionMapper.selectById(sessionId);
        if (session != null) {
            pushStatsToTeacher(interaction, session.getClassId(), session.getTeacherId());
            // OneBot QQ 提醒未答题学生
            liveNotificationService.notifyUnanswered(interaction, session);
        }
        log.info("互动已关闭: interactionId={}", interactionId);
    }

    /** 结束课堂时同步结束仍在作答中的题目，并取消其自动关闭任务。 */
    public void closeActiveInteraction(Long sessionId) {
        Interaction active = interactionMapper.findActiveBySessionId(sessionId);
        if (active != null) closeInteraction(active.getId(), sessionId);
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
        if ("CHOICE".equals(interaction.getType()) && interaction.getCorrectKey() != null) {
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
                .interactionId(interaction.getId()).questionId(interaction.getQuestionId())
                .type(interaction.getType())
                .status("ACTIVE".equals(interaction.getStatus()) ? "ACTIVE" : "CLOSED")
                .title(interaction.getTitle()).description(interaction.getDescription())
                .correctKey("CLOSED".equals(interaction.getStatus()) ? interaction.getCorrectKey() : null)
                .options(options).timeLimit(interaction.getTimeLimit())
                .deadlineEpochMs(toEpochMillis(interaction.getDeadlineAt()))
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
        if (!sessionId.equals(interaction.getSessionId())) {
            throw new BusinessException(ErrorCode.FORBIDDEN.getCode(), "互动不属于当前课堂");
        }
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
        if ("CHOICE".equals(interaction.getType()) && interaction.getCorrectKey() != null && respondedCount > 0) {
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
                .interactionId(interaction.getId()).questionId(interaction.getQuestionId())
                .type(interaction.getType()).title(interaction.getTitle())
                .description(interaction.getDescription()).options(options).correctKey(interaction.getCorrectKey())
                .timeLimit(interaction.getTimeLimit()).status(interaction.getStatus())
                .difficulty(interaction.getDifficulty()).explanation(interaction.getExplanation())
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
            if ("CHOICE".equals(i.getType()) && i.getCorrectKey() != null && respondedCount > 0) {
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
                    .interactionId(i.getId()).questionId(i.getQuestionId())
                    .type(i.getType()).title(i.getTitle())
                    .description(i.getDescription()).options(options)
                    .correctKey(studentId == null || "CLOSED".equals(i.getStatus()) ? i.getCorrectKey() : null)
                    .timeLimit(i.getTimeLimit()).status(i.getStatus()).difficulty(i.getDifficulty())
                    .createdAt(i.getCreatedAt() != null ? i.getCreatedAt().toString() : null)
                    .totalStudents(totalStudents).respondedCount(respondedCount).correctRate(correctRate)
                    .myAnswer(myAnswer)
                    .myCorrect(studentId == null || "CLOSED".equals(i.getStatus()) ? myCorrect : null)
                    .build();
        }).toList();
    }

    /**
     * AI 生成题目（调用 Agent 生成选择题/简答题）
     */
    public Map<String, Object> generateQuestion(String topic, String type) {
        String typeName = "CHOICE".equals(type) ? "选择题（含4个选项A/B/C/D和正确答案）" : "简答题";
        String prompt = String.format(
                "你是一位资深教师。请根据以下知识点生成一道%s。\n知识点：%s\n\n" +
                "输出严格JSON格式（不要markdown代码块）：\n" +
                "{\"type\":\"%s\",\"title\":\"题目内容\",\"options\":[{\"key\":\"A\",\"text\":\"选项A\"},...],\"correctKey\":\"A\"}\n" +
                "如果是简答题，options字段为null，correctKey为参考答案。", typeName, topic, type);

        try {
            InteractionCreateDTO generated = structuredOutputInvoker.invoke(
                    p -> agentService.chat(p, "question-generation"),
                    prompt, InteractionCreateDTO.class, "question-generation",
                    this::validateGeneratedQuestion);
            generated.setType(type);
            return objectMapper.convertValue(generated, Map.class);
        } catch (Exception e) {
            log.error("AI生成题目解析失败: {}", e.getMessage());
            throw new BusinessException(ErrorCode.SYSTEM_ERROR.getCode(), "AI生成失败，请重试");
        }
    }

    private void validateGeneratedQuestion(InteractionCreateDTO result) {
        if ("CHOICE".equals(result.getType())) {
            if (result.getOptions() == null || result.getOptions().size() < 2) {
                throw new StructuredOutputValidationException(List.of("options: choice requires at least two options"));
            }
            boolean keyExists = result.getOptions().stream()
                    .anyMatch(option -> option.getKey().equals(result.getCorrectKey()));
            if (!keyExists) {
                throw new StructuredOutputValidationException(List.of("correctKey: must reference an option"));
            }
        }
    }

    /** 延长当前题目的作答截止时间。延时消息单独推送，避免学生端把它当成一道新题。 */
    @Transactional
    public InteractionTimingDTO extendInteraction(Long sessionId, Long interactionId, int seconds) {
        if (seconds != 30 && seconds != 60 && seconds != 300) {
            throw new BusinessException(ErrorCode.PARAM_ERROR.getCode(), "仅支持延时30秒、1分钟或5分钟");
        }

        lockActiveSession(sessionId);
        Interaction interaction = interactionMapper.selectById(interactionId);
        if (interaction == null || !sessionId.equals(interaction.getSessionId())
                || !"ACTIVE".equals(interaction.getStatus())) {
            throw new BusinessException(ErrorCode.PARAM_ERROR.getCode(), "题目已结束或不属于当前课堂");
        }

        LocalDateTime now = LocalDateTime.now();
        LocalDateTime base = interaction.getDeadlineAt() != null && interaction.getDeadlineAt().isAfter(now)
                ? interaction.getDeadlineAt() : now;
        LocalDateTime deadlineAt = base.plusSeconds(seconds);
        if (interactionMapper.updateDeadline(interactionId, sessionId, deadlineAt) == 0) {
            throw new BusinessException(ErrorCode.DATA_ALREADY_EXISTS.getCode(), "题目状态已变化，请刷新后重试");
        }

        interaction.setDeadlineAt(deadlineAt);
        scheduleAutoCloseAt(interaction);
        InteractionTimingDTO timing = InteractionTimingDTO.builder()
                .interactionId(interactionId)
                .deadlineEpochMs(toEpochMillis(deadlineAt))
                .addedSeconds(seconds)
                .build();
        messagingTemplate.convertAndSend("/topic/session/" + sessionId + "/interaction-timing", timing);
        return timing;
    }

    /** 统一题库与本节课发送记录组成的教师题目看板。 */
    public List<QuestionBoardItemDTO> getQuestionBoard(Long sessionId) {
        ClassroomSession session = sessionMapper.selectById(sessionId);
        if (session == null) {
            throw new BusinessException(ErrorCode.DATA_NOT_FOUND.getCode(), "课堂不存在");
        }

        int totalStudents = Optional.ofNullable(classStudentMapper.countByClassId(session.getClassId())).orElse(0);
        Map<Long, List<InteractionResponse>> responsesByInteraction = responseMapper.findBySessionId(sessionId).stream()
                .collect(Collectors.groupingBy(InteractionResponse::getInteractionId));

        List<Interaction> sentInteractions = interactionMapper.findBySessionId(sessionId);
        Map<Long, List<Interaction>> interactionsByQuestion = sentInteractions.stream()
                .filter(interaction -> interaction.getQuestionId() != null)
                .collect(Collectors.groupingBy(
                        Interaction::getQuestionId,
                        LinkedHashMap::new,
                        Collectors.toList()));

        List<QuestionBankItem> questions = questionService
                .searchEntities(session.getTeacherId(), null, null, null).stream()
                .filter(question -> Set.of("CHOICE", "OPEN", "EXERCISE").contains(question.getType()))
                .toList();
        Set<Long> visibleQuestionIds = questions.stream()
                .map(QuestionBankItem::getId)
                .collect(Collectors.toSet());

        List<QuestionBoardItemDTO> result = new ArrayList<>();
        int sortOrder = 0;
        for (QuestionBankItem question : questions) {
            List<Interaction> attempts = interactionsByQuestion.getOrDefault(question.getId(), List.of());
            Interaction latest = latestInteraction(attempts);
            List<InteractionResponse> responses = latest == null
                    ? List.of()
                    : responsesByInteraction.getOrDefault(latest.getId(), List.of());
            result.add(buildQuestionBoardItem(
                    question, latest, attempts.size(), responses, totalStudents, sortOrder++));
        }

        // 已归档题目仍应在本节课堂中显示，避免课堂进行中突然丢失作答记录。
        for (Map.Entry<Long, List<Interaction>> entry : interactionsByQuestion.entrySet()) {
            if (visibleQuestionIds.contains(entry.getKey())) continue;
            Interaction latest = latestInteraction(entry.getValue());
            if (latest == null) continue;
            result.add(buildQuestionBoardItem(
                    null,
                    latest,
                    entry.getValue().size(),
                    responsesByInteraction.getOrDefault(latest.getId(), List.of()),
                    totalStudents,
                    sortOrder++));
        }
        return result;
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

    private QuestionBoardItemDTO buildQuestionBoardItem(QuestionBankItem question,
                                                         Interaction interaction,
                                                         int sendCount,
                                                         List<InteractionResponse> responses,
                                                         int totalStudents,
                                                         int sortOrder) {
        String optionsJson = interaction != null ? interaction.getOptions() : question.getOptions();
        String type = interaction != null ? interaction.getType() : question.getType();
        String correctKey = interaction != null ? interaction.getCorrectKey() : question.getCorrectKey();
        List<InteractionCreateDTO.OptionDTO> options = parseOptions(optionsJson);
        int respondedCount = responses.size();
        Map<String, Long> rawDistribution = responses.stream()
                .collect(Collectors.groupingBy(
                        response -> response.getAnswer() != null ? response.getAnswer() : "未作答",
                        LinkedHashMap::new,
                        Collectors.counting()));
        Map<String, LiveStatsDTO.DistributionItem> distribution = new LinkedHashMap<>();
        if (options != null) {
            options.forEach(option -> rawDistribution.putIfAbsent(option.getKey(), 0L));
        }
        rawDistribution.forEach((answer, count) -> distribution.put(answer,
                LiveStatsDTO.DistributionItem.builder()
                        .count(count.intValue())
                        .percent(respondedCount > 0
                                ? Math.round(count * 1000.0 / respondedCount) / 10.0
                                : 0.0)
                        .build()));

        Double correctRate = null;
        if ("CHOICE".equals(type) && correctKey != null && respondedCount > 0) {
            long correctCount = responses.stream().filter(r -> Boolean.TRUE.equals(r.getIsCorrect())).count();
            correctRate = Math.round(correctCount * 1000.0 / respondedCount) / 10.0;
        }

        return QuestionBoardItemDTO.builder()
                .questionId(question != null ? question.getId() : interaction.getQuestionId())
                .interactionId(interaction != null ? interaction.getId() : null)
                .type(type)
                .title(interaction != null ? interaction.getTitle() : question.getTitle())
                .description(interaction != null ? interaction.getDescription() : question.getRequirement())
                .options(options)
                .correctKey(correctKey)
                .knowledgePoint(interaction != null ? interaction.getKnowledgePoint() : question.getKnowledgePoint())
                .difficulty(interaction != null ? interaction.getDifficulty() : question.getDifficulty())
                .status(interaction != null ? interaction.getStatus() : "UNSENT")
                .sortOrder(sortOrder)
                .timeLimit(interaction != null ? interaction.getTimeLimit() : question.getDefaultTimeLimit())
                .sendCount(sendCount)
                .createdAt(question != null && question.getCreatedAt() != null
                        ? question.getCreatedAt().toString()
                        : interaction != null && interaction.getCreatedAt() != null
                        ? interaction.getCreatedAt().toString() : null)
                .activatedAt(interaction != null && interaction.getActivatedAt() != null
                        ? interaction.getActivatedAt().toString() : null)
                .deadlineEpochMs(interaction != null ? toEpochMillis(interaction.getDeadlineAt()) : null)
                .totalStudents(totalStudents)
                .respondedCount(respondedCount)
                .correctRate(correctRate)
                .distribution(distribution)
                .build();
    }

    private Interaction latestInteraction(List<Interaction> interactions) {
        return interactions.stream()
                .max(Comparator.comparing(
                                Interaction::getActivatedAt,
                                Comparator.nullsFirst(Comparator.naturalOrder()))
                        .thenComparing(Interaction::getId))
                .orElse(null);
    }

    private List<InteractionCreateDTO.OptionDTO> parseOptions(String optionsJson) {
        if (optionsJson == null || optionsJson.isBlank()) return null;
        try {
            return objectMapper.readValue(optionsJson,
                    objectMapper.getTypeFactory().constructCollectionType(
                            List.class, InteractionCreateDTO.OptionDTO.class));
        } catch (JsonProcessingException e) {
            log.warn("互动选项解析失败", e);
            return null;
        }
    }

    private ClassroomSession lockActiveSession(Long sessionId) {
        ClassroomSession session = sessionMapper.selectByIdForUpdate(sessionId);
        if (session == null || !"ACTIVE".equals(session.getStatus())) {
            throw new BusinessException(ErrorCode.PARAM_ERROR.getCode(), "课堂不存在或已结束");
        }
        return session;
    }

    private void ensureNoActiveInteraction(Long sessionId) {
        if (interactionMapper.findActiveBySessionId(sessionId) != null) {
            throw new BusinessException(ErrorCode.DATA_ALREADY_EXISTS.getCode(), "请先结束当前题目，再发送下一题");
        }
    }

    private LocalDateTime calculateDeadline(LocalDateTime activatedAt, Integer timeLimitSeconds) {
        return timeLimitSeconds != null && timeLimitSeconds > 0
                ? activatedAt.plusSeconds(timeLimitSeconds)
                : null;
    }

    private Long toEpochMillis(LocalDateTime dateTime) {
        return dateTime == null ? null : dateTime.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
    }

    private void scheduleAutoCloseAt(Interaction interaction) {
        if (interaction.getDeadlineAt() == null || interaction.getId() == null || interaction.getSessionId() == null) {
            return;
        }
        ScheduledFuture<?> previous = pendingClosures.remove(interaction.getId());
        if (previous != null) previous.cancel(false);

        long delayMillis = Math.max(0L, Duration.between(LocalDateTime.now(), interaction.getDeadlineAt()).toMillis());
        ScheduledFuture<?> future = autoCloseScheduler.schedule(
                () -> handleAutoClose(interaction.getId(), interaction.getSessionId()),
                delayMillis,
                TimeUnit.MILLISECONDS);
        pendingClosures.put(interaction.getId(), future);
        log.debug("已调度自动关闭: interactionId={}, deadline={}", interaction.getId(), interaction.getDeadlineAt());
    }

    private void handleAutoClose(Long interactionId, Long sessionId) {
        pendingClosures.remove(interactionId);
        try {
            Interaction latest = interactionMapper.selectById(interactionId);
            if (latest == null || !"ACTIVE".equals(latest.getStatus())) return;
            if (latest.getDeadlineAt() != null && latest.getDeadlineAt().isAfter(LocalDateTime.now())) {
                scheduleAutoCloseAt(latest);
                return;
            }
            closeInteraction(interactionId, sessionId);
        } catch (Exception e) {
            log.error("自动关闭互动失败: interactionId={}", interactionId, e);
        }
    }

    /** 服务重启后恢复尚未结束题目的自动关闭任务。 */
    @EventListener(ApplicationReadyEvent.class)
    public void recoverAutoCloseTasks() {
        List<Interaction> activeInteractions = interactionMapper.findActiveWithDeadline();
        activeInteractions.forEach(this::scheduleAutoCloseAt);
        if (!activeInteractions.isEmpty()) {
            log.info("已恢复 {} 个互动自动关闭任务", activeInteractions.size());
        }
    }
}
