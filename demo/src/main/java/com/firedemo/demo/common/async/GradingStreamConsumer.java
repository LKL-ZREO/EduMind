package com.firedemo.demo.common.async;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.firedemo.demo.DTO.EvaluationResultDTO;
import com.firedemo.demo.Entity.HomeworkKnowledge;
import com.firedemo.demo.Entity.HomeworkTask;
import com.firedemo.demo.Entity.Submission;
import com.firedemo.demo.Service.FileStorageService;
import com.firedemo.demo.Service.OneBotHttpService;
import com.firedemo.demo.Service.OpenClawService;
import com.firedemo.demo.mapper.ClassStudentMapper;
import com.firedemo.demo.mapper.HomeworkKnowledgeMapper;
import com.firedemo.demo.mapper.HomeworkTaskMapper;
import com.firedemo.demo.mapper.StudentQqBindingMapper;
import com.firedemo.demo.mapper.SubmissionMapper;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RStream;
import org.redisson.RedissonShutdownException;
import org.redisson.api.RedissonClient;
import org.redisson.api.StreamMessageId;
import org.redisson.api.stream.StreamCreateGroupArgs;
import org.redisson.api.stream.StreamReadGroupArgs;
import org.redisson.client.codec.StringCodec;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 批改任务消费者 — 从 Redis Stream 消费消息，调 OpenClaw 批改，写回数据库
 * <p>
 * 单线程消费，天然保证 AI 调用串行，OpenClaw 不会被打崩。
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
    private final HomeworkKnowledgeMapper knowledgeMapper;
    private final HomeworkTaskMapper taskMapper;
    private final ClassStudentMapper classStudentMapper;
    private final StudentQqBindingMapper studentQqBindingMapper;
    private final ObjectMapper objectMapper;

    private final AtomicBoolean running = new AtomicBoolean(false);
    private ExecutorService executor;
    private String consumerName;

    public GradingStreamConsumer(RedissonClient redissonClient,
                                  GradingStreamProducer gradingStreamProducer,
                                  SubmissionMapper submissionMapper,
                                  FileStorageService fileStorageService,
                                  OpenClawService openClawService,
                                  OneBotHttpService oneBotHttpService,
                                  HomeworkKnowledgeMapper knowledgeMapper,
                                  HomeworkTaskMapper taskMapper,
                                  ClassStudentMapper classStudentMapper,
                                  StudentQqBindingMapper studentQqBindingMapper,
                                  ObjectMapper objectMapper) {
        this.redissonClient = redissonClient;
        this.gradingStreamProducer = gradingStreamProducer;
        this.submissionMapper = submissionMapper;
        this.fileStorageService = fileStorageService;
        this.openClawService = openClawService;
        this.oneBotHttpService = oneBotHttpService;
        this.knowledgeMapper = knowledgeMapper;
        this.taskMapper = taskMapper;
        this.classStudentMapper = classStudentMapper;
        this.studentQqBindingMapper = studentQqBindingMapper;
        this.objectMapper = objectMapper;
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

    private void processOne(StreamMessageId messageId, Map<String, String> data) {
        String submissionIdStr = data.get(AsyncTaskConstants.FIELD_SUBMISSION_ID);
        if (submissionIdStr == null) {
            ack(messageId);
            return;
        }

        Long submissionId = Long.parseLong(submissionIdStr);
        String requirement = data.getOrDefault(AsyncTaskConstants.FIELD_REQUIREMENT, "");
        int retryCount = Integer.parseInt(data.getOrDefault(AsyncTaskConstants.FIELD_RETRY_COUNT, "0"));

        log.info("开始批改: submissionId={}, retryCount={}", submissionId, retryCount);

        Submission submission = submissionMapper.selectById(submissionId);
        if (submission == null) {
            log.warn("提交记录不存在: submissionId={}", submissionId);
            ack(messageId);
            return;
        }

        submission.setStatus("PROCESSING");
        submissionMapper.updateById(submission);

        try {
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

            if (evaluation.getTotalScore() != null && evaluation.getTotalScore() < 60) {
                String qq = studentQqBindingMapper.selectQqByStudentId(submission.getStudentId());
                if (qq != null) {
                    oneBotHttpService.sendPrivateMessage(qq, String.format(
                            "同学你好，你的作业「%s」得分 %d 分，建议多向老师或机器人提问。",
                            submission.getAssignmentName(), evaluation.getTotalScore()));
                }
            }

        } catch (Exception e) {
            log.error("批改失败: submissionId={}, error={}", submissionId, e.getMessage(), e);
            if (retryCount < AsyncTaskConstants.MAX_RETRY) {
                gradingStreamProducer.retryTask(submissionId, requirement, retryCount + 1);
                submission.setStatus("PENDING");
                submissionMapper.updateById(submission);
            } else {
                submission.setStatus("FAILED");
                submission.setErrorMessage(truncate(e.getMessage(), 500));
                submissionMapper.updateById(submission);
            }
        }

        ack(messageId);
    }

    private String buildPrompt(Submission sub, String requirement, String fileContent) {
        return String.format("""
                        请批改以下作业，并以JSON格式返回结果：

                        学生姓名：%s
                        班级：%s
                        作业名称：%s
                        要求：%s

                        文件内容：
                        %s

                        请使用批改作业助手的标准格式输出结果，必须是合法JSON格式。
                        """,
                sub.getStudentName(),
                sub.getClassName(),
                sub.getAssignmentName(),
                requirement != null && !requirement.isEmpty() ? requirement : "无特殊要求",
                fileContent);
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

        if (eval.getKnowledgePoints() != null) {
            for (EvaluationResultDTO.KnowledgePointItem kp : eval.getKnowledgePoints()) {
                HomeworkKnowledge hk = new HomeworkKnowledge();
                hk.setSubmissionId(sub.getId());
                hk.setKnowledgePoint(kp.getName());
                hk.setMastery(kp.getMastery());
                hk.setStatus(kp.getStatus());
                knowledgeMapper.insert(hk);
            }
        }

        if (sub.getClassId() != null && sub.getStudentId() != null
                && sub.getStudentName() != null) {
            classStudentMapper.insertIgnore(sub.getClassId(),
                    sub.getStudentId(), sub.getStudentName(), "auto");
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
