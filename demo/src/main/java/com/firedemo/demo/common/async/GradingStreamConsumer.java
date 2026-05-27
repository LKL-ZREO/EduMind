package com.firedemo.demo.common.async;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.firedemo.demo.DTO.EvaluationResultDTO;
import com.firedemo.demo.Entity.HomeworkTask;
import com.firedemo.demo.Entity.Submission;
import com.firedemo.demo.Entity.SubmissionError;
import com.firedemo.demo.Entity.TeacherKnowledge;
import com.firedemo.demo.Service.FileStorageService;
import com.firedemo.demo.Service.OneBotHttpService;
import com.firedemo.demo.Service.OpenClawService;
import com.firedemo.demo.common.cache.CacheConsistencyService;
import com.firedemo.demo.mapper.ClassStudentMapper;
import com.firedemo.demo.mapper.HomeworkTaskMapper;
import com.firedemo.demo.mapper.StudentQqBindingMapper;
import com.firedemo.demo.mapper.SubmissionErrorMapper;
import com.firedemo.demo.mapper.SubmissionMapper;
import com.firedemo.demo.mapper.TeacherKnowledgeMapper;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.redisson.RedissonShutdownException;
import org.redisson.api.RLock;
import org.redisson.api.RStream;
import org.redisson.api.RedissonClient;
import org.redisson.api.StreamMessageId;
import org.redisson.api.stream.StreamCreateGroupArgs;
import org.redisson.api.stream.StreamReadGroupArgs;
import org.redisson.client.codec.StringCodec;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 批改任务消费者 — 从 Redis Stream 消费消息，调 OpenClaw 批改，写回数据库
 * <p>
 * 单线程消费，天然保证 AI 调用串行，OpenClaw 不会被打崩。
 * <p>
 * 可靠性增强：
 * <ul>
 *   <li>分布式锁防重复消费（同一 submissionId 只处理一次）</li>
 *   <li>Pending 僵尸消息定时认领（消费者宕机后自动接管）</li>
 *   <li>延迟双删保证缓存一致性</li>
 * </ul>
 */
@Slf4j
@Component
public class GradingStreamConsumer {

    private final RedissonClient redissonClient;
    private final GradingStreamProducer gradingStreamProducer;
    private final SubmissionMapper submissionMapper;
    private final FileStorageService fileStorageService;
    private final OpenClawService openClawService;
    private final OneBotHttpService oneBotHttpService;
    private final TeacherKnowledgeMapper teacherKnowledgeMapper;
    private final SubmissionErrorMapper submissionErrorMapper;
    private final HomeworkTaskMapper taskMapper;
    private final ClassStudentMapper classStudentMapper;
    private final StudentQqBindingMapper studentQqBindingMapper;
    private final ObjectMapper objectMapper;
    private final CacheConsistencyService cacheConsistencyService;

    private final AtomicBoolean running = new AtomicBoolean(false);
    private ExecutorService executor;
    private String consumerName;

    public GradingStreamConsumer(RedissonClient redissonClient,
                                  GradingStreamProducer gradingStreamProducer,
                                  SubmissionMapper submissionMapper,
                                  FileStorageService fileStorageService,
                                  OpenClawService openClawService,
                                  OneBotHttpService oneBotHttpService,
                                  TeacherKnowledgeMapper teacherKnowledgeMapper,
                                  SubmissionErrorMapper submissionErrorMapper,
                                  HomeworkTaskMapper taskMapper,
                                  ClassStudentMapper classStudentMapper,
                                  StudentQqBindingMapper studentQqBindingMapper,
                                  ObjectMapper objectMapper,
                                  CacheConsistencyService cacheConsistencyService) {
        this.redissonClient = redissonClient;
        this.gradingStreamProducer = gradingStreamProducer;
        this.submissionMapper = submissionMapper;
        this.fileStorageService = fileStorageService;
        this.openClawService = openClawService;
        this.oneBotHttpService = oneBotHttpService;
        this.teacherKnowledgeMapper = teacherKnowledgeMapper;
        this.submissionErrorMapper = submissionErrorMapper;
        this.taskMapper = taskMapper;
        this.classStudentMapper = classStudentMapper;
        this.studentQqBindingMapper = studentQqBindingMapper;
        this.objectMapper = objectMapper;
        this.cacheConsistencyService = cacheConsistencyService;
    }

    @PostConstruct
    public void start() {
        this.consumerName = AsyncTaskConstants.GRADING_CONSUMER_PREFIX
                + UUID.randomUUID().toString().substring(0, 8);

        RStream<String, String> stream = redissonClient.getStream(
                AsyncTaskConstants.GRADING_STREAM_KEY, StringCodec.INSTANCE);
        try {
            stream.createGroup(StreamCreateGroupArgs.name(AsyncTaskConstants.GRADING_GROUP)
                    .makeStream());
            log.info("消费者组已创建: {}", AsyncTaskConstants.GRADING_GROUP);
        } catch (Exception e) {
            log.info("消费者组已存在，跳过创建: {}", AsyncTaskConstants.GRADING_GROUP);
        }

        this.executor = Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "grading-consumer");
            t.setDaemon(true);
            return t;
        });

        running.set(true);
        executor.submit(this::consumeLoop);
        log.info("批改消费者已启动: consumerName={}", consumerName);
    }

    @PreDestroy
    public void stop() {
        running.set(false);
        if (executor != null) {
            executor.shutdown();
        }
        log.info("批改消费者已关闭: consumerName={}", consumerName);
    }

    // ==================== 消息消费 ====================

    private void consumeLoop() {
        RStream<String, String> stream = redissonClient.getStream(
                AsyncTaskConstants.GRADING_STREAM_KEY, StringCodec.INSTANCE);

        while (running.get()) {
            try {
                Map<StreamMessageId, Map<String, String>> messages = stream.readGroup(
                        AsyncTaskConstants.GRADING_GROUP,
                        consumerName,
                        StreamReadGroupArgs.neverDelivered()
                                .count(AsyncTaskConstants.BATCH_SIZE)
                                .timeout(Duration.ofMillis(AsyncTaskConstants.POLL_TIMEOUT_MS))
                );

                if (messages == null || messages.isEmpty()) {
                    continue;
                }

                for (Map.Entry<StreamMessageId, Map<String, String>> entry : messages.entrySet()) {
                    processOne(entry.getKey(), entry.getValue());
                }
            } catch (RedissonShutdownException e) {
                log.info("Redisson 已关闭，消费者退出");
                break;
            } catch (Exception e) {
                if (Thread.currentThread().isInterrupted()) {
                    log.info("消费者线程被中断");
                    break;
                }
                log.error("消费异常", e);
            }
        }
    }

    /**
     * 定时扫描 Pending 队列 — 僵尸消息兜底
     * <p>
     * 当消费者宕机时，其未 ACK 的消息会永久停留在 Pending 队列。
     * claim() 改变消息 owner 并返回消息数据，提交到消费线程处理后 ACK。
     */
    @Scheduled(fixedDelay = 30_000)
    @SuppressWarnings("unchecked")
    public void claimPendingMessages() {
        try {
            RStream<String, String> stream = redissonClient.getStream(
                    AsyncTaskConstants.GRADING_STREAM_KEY, StringCodec.INSTANCE);
            java.util.List rawList = (java.util.List) stream.listPending(
                    AsyncTaskConstants.GRADING_GROUP,
                    StreamMessageId.MIN,
                    StreamMessageId.MAX,
                    100);

            if (rawList == null || rawList.isEmpty()) {
                return;
            }

            int claimedCount = 0;
            for (Object item : rawList) {
                try {
                    StreamMessageId msgId = extractMessageId(item);
                    long idleTime = extractIdleTime(item);
                    if (idleTime > 60_000) {
                        // claim 返回被认领的消息数据，直接处理
                        Map<StreamMessageId, Map<String, String>> claimed = stream.claim(
                                AsyncTaskConstants.GRADING_GROUP,
                                this.consumerName, idleTime, TimeUnit.MILLISECONDS, msgId);

                        if (claimed != null && !claimed.isEmpty()) {
                            for (Map.Entry<StreamMessageId, Map<String, String>> entry : claimed.entrySet()) {
                                executor.submit(() -> processOne(entry.getKey(), entry.getValue()));
                                claimedCount++;
                            }
                        }
                    }
                } catch (Exception ignored) {
                }
            }
            if (claimedCount > 0) {
                log.info("认领并处理僵尸消息: count={}", claimedCount);
            }
        } catch (Exception e) {
            log.debug("Pending扫描结束: {}", e.getMessage());
        }
    }

    private StreamMessageId extractMessageId(Object entry) throws Exception {
        if (entry instanceof StreamMessageId) {
            return (StreamMessageId) entry;
        }
        return (StreamMessageId) entry.getClass().getMethod("getId").invoke(entry);
    }

    private long extractIdleTime(Object entry) throws Exception {
        if (entry instanceof StreamMessageId) {
            return Long.MAX_VALUE;
        }
        return (long) entry.getClass().getMethod("getIdleTime").invoke(entry);
    }

    // ==================== 消息处理 ====================

    private void processOne(StreamMessageId messageId, Map<String, String> data) {
        String submissionIdStr = data.get(AsyncTaskConstants.FIELD_SUBMISSION_ID);
        if (submissionIdStr == null) {
            ack(messageId);
            return;
        }

        Long submissionId = Long.parseLong(submissionIdStr);

        // 分布式锁防重复消费
        RLock lock = redissonClient.getLock("lock:grading:" + submissionId);
        try {
            if (!lock.tryLock(0, 120, TimeUnit.SECONDS)) {
                log.info("批改任务已在处理中，跳过: submissionId={}", submissionId);
                ack(messageId);
                return;
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            ack(messageId);
            return;
        }

        try {
            String requirement = data.getOrDefault(AsyncTaskConstants.FIELD_REQUIREMENT, "");
            int retryCount = Integer.parseInt(data.getOrDefault(AsyncTaskConstants.FIELD_RETRY_COUNT, "0"));

            log.info("开始批改: submissionId={}, retryCount={}", submissionId, retryCount);

            Submission submission = submissionMapper.selectById(submissionId);
            if (submission == null) {
                log.warn("提交记录不存在: submissionId={}", submissionId);
                return;
            }

            submission.setStatus("PROCESSING");
            submissionMapper.updateById(submission);

            String fileContent = fileStorageService.readFileContent(submission.getFilePath());
            if (fileContent == null || fileContent.isEmpty()) {
                throw new RuntimeException("无法读取文件内容");
            }

            String prompt = buildPrompt(submission, requirement, fileContent);
            String response = openClawService.chat(prompt, "grading_" + submissionId);
            String jsonStr = extractJson(response);
            EvaluationResultDTO evaluation = objectMapper.readValue(jsonStr, EvaluationResultDTO.class);

            saveEvaluation(submission, evaluation, response);

            submission.setStatus("COMPLETED");
            submissionMapper.updateById(submission);

            log.info("批改完成: submissionId={}, score={}", submissionId, evaluation.getTotalScore());

            // 延迟双删 — 缓存一致性
            if (submission.getClassId() != null) {
                cacheConsistencyService.evictWithDoubleDelete("dashboard",
                        "metrics:" + submission.getClassId(),
                        "scoreDist:" + submission.getClassId(),
                        "knowledge:" + submission.getClassId(),
                        "errors:" + submission.getClassId());
            }

            if (evaluation.getTotalScore() != null && evaluation.getTotalScore() < 60) {
                String qq = studentQqBindingMapper.selectQqByStudentId(submission.getStudentId());
                if (qq != null) {
                    oneBotHttpService.sendPrivateMessage(qq, String.format(
                            "同学你好，你的作业「%s」得分 %d 分，建议多向老师或机器人提问。",
                            submission.getAssignmentName(), evaluation.getTotalScore()));
                }
            }

        } catch (Exception e) {
            log.error("批改失败: submissionId={}", submissionId, e);
            int retryCount = Integer.parseInt(data.getOrDefault(AsyncTaskConstants.FIELD_RETRY_COUNT, "0"));
            String requirement = data.getOrDefault(AsyncTaskConstants.FIELD_REQUIREMENT, "");
            if (retryCount < AsyncTaskConstants.MAX_RETRY) {
                gradingStreamProducer.retryTask(submissionId, requirement, retryCount + 1);
                Submission pending = submissionMapper.selectById(submissionId);
                if (pending != null) {
                    pending.setStatus("PENDING");
                    submissionMapper.updateById(pending);
                }
            } else {
                Submission failed = submissionMapper.selectById(submissionId);
                if (failed != null) {
                    failed.setStatus("FAILED");
                    failed.setErrorMessage(truncate(e.getMessage(), 500));
                    submissionMapper.updateById(failed);
                }
            }
        } finally {
            if (lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
        }

        ack(messageId);
    }

    // ==================== 辅助方法 ====================

    private String buildPrompt(Submission sub, String requirement, String fileContent) {
        // 查询该班级教师定义的知识点列表
        String kpContext = "";
        List<TeacherKnowledge> kps = teacherKnowledgeMapper.selectByClassId(sub.getClassId());
        if (kps != null && !kps.isEmpty()) {
            List<String> kpNames = kps.stream()
                    .map(TeacherKnowledge::getName)
                    .collect(java.util.stream.Collectors.toList());
            kpContext = String.format("""

                        该班级教师定义的知识点列表：
                        %s

                        请根据以上知识点列表，将每条 errors[] 和 suggestions[] 归类到对应的知识点。
                        如果某个错误/建议不属于列表中的任何一个知识点，则 knowledgePoint 填 "其他"。
                        """, kpNames);
        }

        return String.format("""
                        请批改以下作业，并以JSON格式返回结果：

                        学生姓名：%s
                        班级：%s
                        作业名称：%s
                        要求：%s

                        文件内容：
                        %s%s

                        请使用批改作业助手的标准格式输出结果，必须是合法JSON格式。
                        """,
                sub.getStudentName(),
                sub.getClassName(),
                sub.getAssignmentName(),
                requirement != null && !requirement.isEmpty() ? requirement : "无特殊要求",
                fileContent,
                kpContext);
    }

    private void saveEvaluation(Submission sub, EvaluationResultDTO eval, String rawResponse) {
        sub.setTotalScore(eval.getTotalScore());
        sub.setContentScore(eval.getContentScore() != null
                ? eval.getContentScore() : eval.getTotalScore());
        sub.setOverallComment(eval.getOverallComment());
        sub.setStrengths(eval.getStrengths() != null
                ? String.join(",", eval.getStrengths()) : "");
        sub.setWeaknesses(eval.getWeaknesses() != null
                ? String.join(",", eval.getWeaknesses()) : "");

        String suggestionsStr = "";
        if (eval.getSuggestions() != null) {
            suggestionsStr = eval.getSuggestions().stream()
                    .map(s -> s.getPriority() + ": " + s.getIssue() + " -> " + s.getSuggestion())
                    .reduce((a, b) -> a + "\n" + b)
                    .orElse("");
        }
        sub.setSuggestions(suggestionsStr);
        sub.setRawResponse(rawResponse);

        Long taskId = sub.getTaskId();
        if (taskId != null) {
            HomeworkTask task = taskMapper.selectById(taskId);
            if (task != null && task.getDeadline() != null
                    && LocalDateTime.now().isAfter(task.getDeadline())) {
                sub.setIsLate(true);
                if (task.getAllowLate() != null && task.getAllowLate()
                        && task.getLatePenalty() != null && task.getLatePenalty() > 0) {
                    sub.setPenaltyApplied(true);
                    long days = java.time.Duration.between(task.getDeadline(), LocalDateTime.now()).toDays();
                    int penalty = (int) Math.ceil(days) * task.getLatePenalty();
                    int finalScore = Math.max(0, (eval.getTotalScore() != null
                            ? eval.getTotalScore() : 0) - penalty);
                    sub.setFinalScore(finalScore);
                }
            }
        }

        // 保存 errors/suggestions 到 submission_errors（按知识点归类）
        saveSubmissionErrors(sub, eval);

        if (sub.getClassId() != null && sub.getStudentId() != null
                && sub.getStudentName() != null) {
            classStudentMapper.insertIgnore(sub.getClassId(),
                    sub.getStudentId(), sub.getStudentName(), "auto");
        }
    }

    private void saveSubmissionErrors(Submission sub, EvaluationResultDTO eval) {
        Long classId = sub.getClassId();
        if (classId == null) return;

        LocalDateTime now = LocalDateTime.now();

        // 写入 errors[]
        if (eval.getErrors() != null) {
            for (EvaluationResultDTO.ErrorItem err : eval.getErrors()) {
                if (err.getIssue() == null || err.getIssue().isEmpty()) continue;
                SubmissionError se = new SubmissionError();
                se.setSubmissionId(sub.getId());
                se.setClassId(classId);
                se.setErrorText(err.getIssue());
                se.setErrorType(err.getType() != null ? err.getType() : "");
                se.setSeverity(err.getSeverity() != null ? err.getSeverity() : "minor");
                se.setKnowledgePoint(err.getKnowledgePoint() != null ? err.getKnowledgePoint() : "其他");
                se.setCreatedAt(now);
                se.setUpdatedAt(now);
                submissionErrorMapper.insert(se);
            }
        }

        // 写入 suggestions[]
        if (eval.getSuggestions() != null) {
            for (EvaluationResultDTO.SuggestionItem sug : eval.getSuggestions()) {
                if (sug.getIssue() == null || sug.getIssue().isEmpty()) continue;
                SubmissionError se = new SubmissionError();
                se.setSubmissionId(sub.getId());
                se.setClassId(classId);
                se.setErrorText(sug.getIssue() + " -> " + (sug.getSuggestion() != null ? sug.getSuggestion() : ""));
                se.setErrorType("");
                se.setSeverity(sug.getPriority() != null ? sug.getPriority() : "low");
                se.setKnowledgePoint(sug.getKnowledgePoint() != null ? sug.getKnowledgePoint() : "其他");
                se.setCreatedAt(now);
                se.setUpdatedAt(now);
                submissionErrorMapper.insert(se);
            }
        }
    }

    private String extractJson(String response) {
        if (response == null || response.isEmpty()) {
            return "{}";
        }
        String trimmed = response.trim();
        int jsonStart = trimmed.indexOf('{');
        int jsonEnd = trimmed.lastIndexOf('}');
        if (jsonStart != -1 && jsonEnd != -1 && jsonEnd > jsonStart) {
            return trimmed.substring(jsonStart, jsonEnd + 1);
        }
        return response;
    }

    private void ack(StreamMessageId messageId) {
        try {
            RStream<String, String> stream = redissonClient.getStream(
                    AsyncTaskConstants.GRADING_STREAM_KEY, StringCodec.INSTANCE);
            stream.ack(AsyncTaskConstants.GRADING_GROUP, messageId);
        } catch (Exception e) {
            log.error("ACK失败: messageId={}", messageId, e);
        }
    }

    private String truncate(String text, int maxLen) {
        if (text == null) {
            return null;
        }
        return text.length() > maxLen ? text.substring(0, maxLen) : text;
    }
}
