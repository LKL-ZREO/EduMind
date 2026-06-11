package com.firedemo.demo.common.async;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.firedemo.demo.DTO.EvaluationResultDTO;
import com.firedemo.demo.Entity.HomeworkTask;
import com.firedemo.demo.Entity.Submission;
import com.firedemo.demo.Entity.SubmissionError;
import com.firedemo.demo.Entity.TeacherKnowledge;
import com.firedemo.demo.Service.FileStorageService;
import com.firedemo.demo.Service.OneBotHttpService;
import com.firedemo.demo.common.enums.SubmissionStatus;
import com.firedemo.demo.Service.OpenClawService;
import com.firedemo.demo.common.ai.StructuredOutputInvoker;
import com.firedemo.demo.common.cache.CacheConsistencyService;
import com.firedemo.demo.common.cache.RedisCacheReader;
import com.firedemo.demo.common.prompt.PromptLoader;
import com.firedemo.demo.mapper.ClassStudentMapper;
import com.firedemo.demo.mapper.HomeworkTaskMapper;
import com.firedemo.demo.mapper.StudentQqBindingMapper;
import com.firedemo.demo.mapper.SubmissionErrorMapper;
import com.firedemo.demo.mapper.SubmissionMapper;
import com.firedemo.demo.mapper.TeacherKnowledgeMapper;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.redisson.api.StreamMessageId;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * 批改任务消费者 — 关注业务逻辑，框架代码由 {@link AbstractStreamConsumer} 提供
 */
@Slf4j
@Component
public class GradingStreamConsumer extends AbstractStreamConsumer {

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
    private final RedisCacheReader redisCacheReader;
    private final StructuredOutputInvoker structuredOutputInvoker;
    private final PromptLoader promptLoader;

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
                                  CacheConsistencyService cacheConsistencyService,
                                  RedisCacheReader redisCacheReader,
                                  StructuredOutputInvoker structuredOutputInvoker,
                                  PromptLoader promptLoader) {
        super(redissonClient);
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
        this.redisCacheReader = redisCacheReader;
        this.structuredOutputInvoker = structuredOutputInvoker;
        this.promptLoader = promptLoader;
    }

    // ==================== 钩子方法（身份标识） ====================

    @Override protected String taskName() { return "批改任务"; }
    @Override protected String streamKey() { return AsyncTaskConstants.GRADING_STREAM_KEY; }
    @Override protected String groupName() { return AsyncTaskConstants.GRADING_GROUP; }
    @Override protected String consumerPrefix() { return AsyncTaskConstants.GRADING_CONSUMER_PREFIX; }
    @Override protected String threadName() { return "grading-consumer"; }
    @Override protected int batchSize() { return AsyncTaskConstants.BATCH_SIZE; }
    @Override protected long pollTimeoutMs() { return AsyncTaskConstants.POLL_TIMEOUT_MS; }

    // ==================== 业务处理（唯一核心方法） ====================

    @Override
    protected void processOne(StreamMessageId messageId, Map<String, String> data) {
        String submissionIdStr = data.get(AsyncTaskConstants.FIELD_SUBMISSION_ID);
        if (submissionIdStr == null) {
            ack(messageId);
            return;
        }

        Long submissionId = Long.parseLong(submissionIdStr);
        String studentRequirement = data.getOrDefault(AsyncTaskConstants.FIELD_REQUIREMENT, "");
        int retryCount = parseRetryCount(data);

        // 分布式锁防重复消费
        RLock lock = redisson().getLock("lock:grading:" + submissionId);
        try {
            if (!lock.tryLock(0, 180, TimeUnit.SECONDS)) {
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
            log.info("开始批改: submissionId={}, retryCount={}", submissionId, retryCount);

            Submission submission = submissionMapper.selectById(submissionId);
            if (submission == null) {
                log.warn("提交记录不存在: submissionId={}", submissionId);
                return;
            }

            // 防重复处理
            if (SubmissionStatus.isFinal(submission.getStatus())) {
                log.info("提交已终态，跳过: submissionId={}", submissionId);
                ack(messageId);
                return;
            }

            // 执行批改
            doGrade(submission, studentRequirement);

        } catch (Exception e) {
            log.error("批改失败: submissionId={}", submissionId, e);
            if (retryCount < AsyncTaskConstants.MAX_RETRY) {
                gradingStreamProducer.retryTask(submissionId, studentRequirement, retryCount + 1);
                Submission pending = submissionMapper.selectById(submissionId);
                if (pending != null) {
                    pending.setStatus(SubmissionStatus.PENDING.getCode());
                    submissionMapper.updateById(pending);
                }
            } else {
                Submission failed = submissionMapper.selectById(submissionId);
                if (failed != null) {
                    failed.setStatus(SubmissionStatus.FAILED.getCode());
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

    // ==================== 批改核心流程 ====================

    private void doGrade(Submission submission, String studentRequirement) throws Exception {
        Long submissionId = submission.getId();

        // 1. 状态标记
        submission.setStatus(SubmissionStatus.PROCESSING.getCode());
        submissionMapper.updateById(submission);

        // 2. 获取作业描述
        String taskDescription = "";
        if (submission.getTaskId() != null) {
            HomeworkTask task = redisCacheReader.read(
                    "task:" + submission.getTaskId(),
                    java.time.Duration.ofMinutes(30),
                    () -> taskMapper.selectById(submission.getTaskId()));
            if (task != null && task.getDescription() != null && !task.getDescription().isEmpty()) {
                taskDescription = task.getDescription();
            }
        }

        // 3. 读取文件
        String fileContent = fileStorageService.readFileContent(submission.getFilePath());
        if (fileContent == null || fileContent.isEmpty()) {
            throw new RuntimeException("无法读取文件内容");
        }

        // 4. 调用 AI 批改
        String prompt = buildPrompt(submission, taskDescription, studentRequirement, fileContent);
        EvaluationResultDTO evaluation = structuredOutputInvoker.invoke(
                p -> openClawService.chat(p, "grading_" + submissionId),
                prompt,
                EvaluationResultDTO.class,
                "作业批改 submissionId=" + submissionId);
        String response = ""; // StructuredOutputInvoker 内部已处理，此处不再返回原始响应

        // 5. 保存结果
        saveEvaluation(submission, evaluation, response);
        submission.setStatus(SubmissionStatus.COMPLETED.getCode());
        submissionMapper.updateById(submission);

        log.info("批改完成: submissionId={}, score={}", submissionId, evaluation.getTotalScore());

        // 6. 缓存一致性
        if (submission.getClassId() != null) {
            cacheConsistencyService.evictWithDoubleDelete("dashboard",
                    "metrics:" + submission.getClassId(),
                    "scoreDist:" + submission.getClassId(),
                    "knowledge:" + submission.getClassId(),
                    "errors:" + submission.getClassId());
        }

        // 7. 低分提醒
        if (evaluation.getTotalScore() != null && evaluation.getTotalScore() < 60) {
            String qq = studentQqBindingMapper.selectQqByStudentId(submission.getStudentId());
            if (qq != null) {
                oneBotHttpService.sendPrivateMessage(qq, String.format(
                        "同学你好，你的作业「%s」得分 %d 分，建议多向老师或机器人提问。",
                        submission.getAssignmentName(), evaluation.getTotalScore()));
            }
        }
    }

    // ==================== 辅助方法 ====================

    private String buildPrompt(Submission sub, String taskDescription,
                                String studentRequirement, String fileContent) {
        // 查询知识点列表
        String kpContext = "";
        List<TeacherKnowledge> kps = teacherKnowledgeMapper.selectByClassId(sub.getClassId());
        if (kps != null && !kps.isEmpty()) {
            List<String> kpNames = kps.stream().map(TeacherKnowledge::getName).collect(Collectors.toList());
            kpContext = String.format("""
                    该班级教师定义的知识点列表：
                    %s

                    请根据以上知识点列表，将每条 errors[] 和 suggestions[] 归类到对应的知识点。
                    如果某个错误/建议不属于列表中的任何一个知识点，则 knowledgePoint 填 "其他"。
                    """, kpNames);
        }

        String studentNote = "";
        if (studentRequirement != null && !studentRequirement.isEmpty()) {
            studentNote = String.format("\n\n【学生提交说明】\n%s\n", studentRequirement);
        }

        String template = promptLoader.load("grading-system.txt");
        return template
                .replace("{{taskDescription}}",
                        taskDescription != null && !taskDescription.isEmpty() ? taskDescription : "无特殊要求")
                .replace("{{studentName}}", sub.getStudentName())
                .replace("{{className}}", sub.getClassName())
                .replace("{{assignmentName}}", sub.getAssignmentName())
                .replace("{{studentNote}}", studentNote)
                .replace("{{fileContent}}", fileContent)
                .replace("{{kpContext}}", kpContext);
    }

    private void saveEvaluation(Submission sub, EvaluationResultDTO eval, String rawResponse) {
        sub.setTotalScore(eval.getTotalScore());
        sub.setContentScore(eval.getContentScore() != null ? eval.getContentScore() : eval.getTotalScore());
        sub.setOverallComment(eval.getOverallComment());
        sub.setStrengths(eval.getStrengths() != null ? String.join(",", eval.getStrengths()) : "");
        sub.setWeaknesses(eval.getWeaknesses() != null ? String.join(",", eval.getWeaknesses()) : "");

        String suggestionsStr = "";
        if (eval.getSuggestions() != null) {
            suggestionsStr = eval.getSuggestions().stream()
                    .map(s -> s.getPriority() + ": " + s.getIssue() + " -> " + s.getSuggestion())
                    .reduce((a, b) -> a + "\n" + b).orElse("");
        }
        sub.setSuggestions(suggestionsStr);
        sub.setRawResponse(rawResponse);

        // 延迟提交扣分
        Long taskId = sub.getTaskId();
        if (taskId != null) {
            HomeworkTask task = redisCacheReader.read(
                    "task:" + taskId,
                    java.time.Duration.ofMinutes(30),
                    () -> taskMapper.selectById(taskId));
            if (task != null && task.getDeadline() != null
                    && LocalDateTime.now().isAfter(task.getDeadline())) {
                sub.setIsLate(true);
                if (task.getAllowLate() != null && task.getAllowLate()
                        && task.getLatePenalty() != null && task.getLatePenalty() > 0) {
                    sub.setPenaltyApplied(true);
                    long days = java.time.Duration.between(task.getDeadline(), LocalDateTime.now()).toDays();
                    int penalty = (int) Math.ceil(days) * task.getLatePenalty();
                    sub.setFinalScore(Math.max(0, (eval.getTotalScore() != null ? eval.getTotalScore() : 0) - penalty));
                }
            }
        }

        saveSubmissionErrors(sub, eval);

        if (sub.getClassId() != null && sub.getStudentId() != null && sub.getStudentName() != null) {
            classStudentMapper.insertIgnore(sub.getClassId(), sub.getStudentId(), sub.getStudentName(), "auto");
        }
    }

    private void saveSubmissionErrors(Submission sub, EvaluationResultDTO eval) {
        Long classId = sub.getClassId();
        if (classId == null) return;

        LocalDateTime now = LocalDateTime.now();

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


}
