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
import java.util.ArrayList;
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

    /** grading-system.txt 模板的 SHA-256，模板改动后缓存自动失效 */
    private String promptTemplateHash;

    // ==================== 初始化 ====================

    @jakarta.annotation.PostConstruct
    void initCacheKey() {
        String template = promptLoader.load("grading-system.txt");
        this.promptTemplateHash = sha256(template);
        log.info("批改缓存模板哈希: {}", promptTemplateHash);
    }

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
                // 其他实例正在处理，不 ack — 由持锁实例处理完成后 ack。
                // 如果持锁实例崩溃，锁 180s 过期后消息会被 claim 并重试。
                log.info("批改任务已在处理中，跳过: submissionId={}", submissionId);
                return;
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            // 未获取锁，不 ack — 消息留在 pending 等待后续重试
            return;
        }

        try {
            log.info("开始批改: submissionId={}, retryCount={}", submissionId, retryCount);

            Submission submission = submissionMapper.selectById(submissionId);
            if (submission == null) {
                log.warn("提交记录不存在: submissionId={}", submissionId);
                ack(messageId);
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

        // 4. 查询知识点（同时用于缓存键和 prompt）
        String kpNames = buildKpNames(submission.getClassId());
        String kpContext = buildKpContext(kpNames);

        // 5. 查缓存 — 命中则跳过 LLM 调用（key = submissionId + 题目 + 知识点 + 模板哈希）
        String cacheKey = buildCacheKey(submissionId, taskDescription, kpNames);
        EvaluationResultDTO evaluation = getCachedResult(cacheKey);

        if (evaluation == null) {
            // 缓存未命中 — 调用 AI 批改
            String prompt = buildPrompt(submission, taskDescription, studentRequirement, fileContent, kpContext);
            evaluation = structuredOutputInvoker.invoke(
                    p -> openClawService.chat(p, "grading_" + submissionId),
                    prompt,
                    EvaluationResultDTO.class,
                    "作业批改 submissionId=" + submissionId);
            cacheResult(cacheKey, evaluation);
        } else {
            log.info("批改缓存命中，跳过LLM: submissionId={}, score={}", submissionId, evaluation.getTotalScore());
        }

        // 6. 保存结果（缓存命中和未命中都走这里）
        saveEvaluation(submission, evaluation, "");
        submission.setStatus(SubmissionStatus.COMPLETED.getCode());
        submissionMapper.updateById(submission);

        log.info("批改完成: submissionId={}, score={}", submissionId, evaluation.getTotalScore());

        // 7. 缓存一致性
        if (submission.getClassId() != null) {
            cacheConsistencyService.evictWithDoubleDelete("dashboard",
                    "metrics:" + submission.getClassId(),
                    "scoreDist:" + submission.getClassId(),
                    "knowledge:" + submission.getClassId(),
                    "errors:" + submission.getClassId());
        }

        // 8. 低分提醒
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

    // ==================== 知识点辅助方法 ====================

    /**
     * 获取知识点名称列表（逗号分隔，用于缓存键）
     */
    private String buildKpNames(Long classId) {
        if (classId == null) return "";
        List<TeacherKnowledge> kps = teacherKnowledgeMapper.selectByClassId(classId);
        if (kps == null || kps.isEmpty()) return "";
        return kps.stream().map(TeacherKnowledge::getName).sorted().collect(Collectors.joining(","));
    }

    /**
     * 构建知识点上下文（用于 prompt 注入）
     */
    private String buildKpContext(String kpNames) {
        if (kpNames == null || kpNames.isEmpty()) return "";
        return String.format("""
                该班级教师定义的知识点列表：
                %s

                请根据以上知识点列表，将每条 errors[] 和 suggestions[] 归类到对应的知识点。
                如果某个错误/建议不属于列表中的任何一个知识点，则 knowledgePoint 填 "其他"。
                """, kpNames);
    }

    // ==================== Prompt 构建 ====================

    private String buildPrompt(Submission sub, String taskDescription,
                                String studentRequirement, String fileContent,
                                String kpContext) {
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
                .replace("{{kpContext}}", kpContext != null ? kpContext : "");
    }

    private void saveEvaluation(Submission sub, EvaluationResultDTO eval, String rawResponse) {
        sub.setTotalScore(eval.getTotalScore());
        sub.setContentScore(eval.getContentScore() != null ? eval.getContentScore() : eval.getTotalScore());
        sub.setFormatScore(eval.getFormatScore() != null ? eval.getFormatScore() : 0);
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
                    long rawDays = java.time.Duration.between(task.getDeadline(), LocalDateTime.now()).toDays();
                    int days = (int) Math.min(rawDays, 30);  // 最多扣 30 天，防止溢出
                    int penalty = days * task.getLatePenalty();
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
        List<SubmissionError> batch = new ArrayList<>();

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
                batch.add(se);
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
                batch.add(se);
            }
        }

        // 批量插入替代逐条 INSERT — 一次 PL/pgSQL 调用完成
        if (!batch.isEmpty()) {
            submissionErrorMapper.insertBatch(batch);
        }
    }

    // ==================== 批改结果缓存 ====================

    /**
     * 构建缓存键：SHA-256(submissionId + 题目 + 知识点 + 模板哈希)
     * <p>
     * <b>不再包含 fileContent。</b> submissionId 已经唯一标识了代码版本：
     * <ul>
     *   <li>同一 submissionId 重复批改 → 缓存命中（省 AI 调用）</li>
     *   <li>学生修改代码后<b>重新提交</b> → 新 submissionId → 新 key → 不会命中旧缓存</li>
     *   <li>老师修改了题目或知识点后重批 → key 中 taskDescription/kpNames 变了 → 缓存 miss</li>
     * </ul>
     */
    private String buildCacheKey(Long submissionId, String taskDescription, String kpNames) {
        String raw = String.join("|",
                String.valueOf(submissionId),
                taskDescription != null ? taskDescription : "",
                kpNames != null ? kpNames : "",
                promptTemplateHash);
        return "grading:cache:" + sha256(raw);
    }

    /**
     * 从 Redis 读取缓存的批改结果
     */
    private EvaluationResultDTO getCachedResult(String cacheKey) {
        try {
            String json = redisson().<String>getBucket(cacheKey).get();
            if (json != null) {
                log.info("批改缓存命中: key={}", cacheKey);
                return objectMapper.readValue(json, EvaluationResultDTO.class);
            }
        } catch (Exception e) {
            log.warn("读取批改缓存失败: key={}", cacheKey, e);
        }
        return null;
    }

    /**
     * 将批改结果写入 Redis，TTL 7 天
     */
    private void cacheResult(String cacheKey, EvaluationResultDTO evaluation) {
        try {
            String json = objectMapper.writeValueAsString(evaluation);
            redisson().<String>getBucket(cacheKey).set(json, 7, java.util.concurrent.TimeUnit.DAYS);
            log.info("批改结果已缓存: key={}", cacheKey);
        } catch (Exception e) {
            log.warn("写入批改缓存失败: key={}", cacheKey, e);
        }
    }

    // ==================== 工具方法 ====================

    /**
     * SHA-256 哈希（hex 字符串）
     */
    private static String sha256(String input) {
        try {
            java.security.MessageDigest md = java.security.MessageDigest.getInstance("SHA-256");
            byte[] digest = md.digest(input.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : digest) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 not available", e);
        }
    }

}
