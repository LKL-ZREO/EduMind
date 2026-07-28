package com.firedemo.demo.live.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.firedemo.demo.DTO.GenerateMaterialsRequest;
import com.firedemo.demo.DTO.GenerateMaterialsResponse;
import com.firedemo.demo.DTO.QuizGenerationResult;
import com.firedemo.demo.Entity.ClassInfo;
import com.firedemo.demo.Entity.Document;
import com.firedemo.demo.Entity.Interaction;
import com.firedemo.demo.Entity.PreviewTask;
import com.firedemo.demo.Entity.QuestionBankItem;
import com.firedemo.demo.Service.FileStorageService;
import com.firedemo.demo.Service.OpenClawService;
import com.firedemo.demo.common.exception.BusinessException;
import com.firedemo.demo.common.exception.ErrorCode;
import com.firedemo.demo.infrastructure.prompt.PromptLoader;
import com.firedemo.demo.infrastructure.ai.StructuredOutputInvoker;
import com.firedemo.demo.infrastructure.ai.StructuredOutputValidationException;
import com.firedemo.demo.mapper.ClassInfoMapper;
import com.firedemo.demo.mapper.DocumentMapper;
import com.firedemo.demo.mapper.InteractionMapper;
import com.firedemo.demo.mapper.PreviewTaskMapper;
import com.firedemo.demo.mapper.QuestionBankItemMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class TeachingMaterialGenerator {

    private final OpenClawService openClawService;
    private final FileStorageService fileStorageService;
    private final PromptLoader promptLoader;
    private final ObjectMapper objectMapper;
    private final StructuredOutputInvoker structuredOutputInvoker;
    private final PreviewTaskMapper previewTaskMapper;
    private final InteractionMapper interactionMapper;
    private final DocumentMapper documentMapper;
    private final LiveNotificationService liveNotificationService;
    private final ClassInfoMapper classInfoMapper;
    private final QuestionBankItemMapper questionBankItemMapper;

    private static final ExecutorService AI_EXECUTOR =
            Executors.newFixedThreadPool(4, r -> {
                Thread t = new Thread(r, "ai-gen-");
                t.setDaemon(true);
                return t;
            });

    private static final int MAX_CONTENT_CHARS = 32_000;
    private static final int AI_TIMEOUT_SECONDS = 90;

    public GenerateMaterialsResponse generate(GenerateMaterialsRequest req, Long teacherId) {
        Document doc = documentMapper.selectByDocId(req.getDocId());
        if (doc == null) {
            throw new BusinessException(ErrorCode.DATA_NOT_FOUND.getCode(), "文件不存在");
        }

        String content = fileStorageService.readFileContent(doc.getFilePath());
        if (content == null || content.isBlank()) {
            throw new BusinessException(ErrorCode.PARAM_ERROR.getCode(), "无法解析文件内容，请确认文件格式");
        }

        content = truncateAtParagraph(content, MAX_CONTENT_CHARS);

        String previewPrompt = promptLoader.load("ppt-preview-generation.txt")
                .replace("{{content}}", content);
        String quizPrompt = promptLoader.load("ppt-quiz-generation.txt")
                .replace("{{content}}", content);

        CompletableFuture<GenerateMaterialsResponse.PreviewItem> previewFuture = CompletableFuture.supplyAsync(() -> {
            try {
                return structuredOutputInvoker.invoke(
                        p -> openClawService.chat(p, null), previewPrompt,
                        GenerateMaterialsResponse.PreviewItem.class, "ppt-preview-generation",
                        this::validatePreview);
            } catch (Exception e) {
                log.error("预习作业生成失败", e);
                return null;
            }
        }, AI_EXECUTOR);

        CompletableFuture<QuizGenerationResult> quizFuture = CompletableFuture.supplyAsync(() -> {
            try {
                return structuredOutputInvoker.invoke(
                        p -> openClawService.chat(p, null), quizPrompt,
                        QuizGenerationResult.class, "ppt-quiz-generation",
                        result -> validateQuizzes(result.getQuizzes()));
            } catch (Exception e) {
                log.error("课堂试题生成失败", e);
                return null;
            }
        }, AI_EXECUTOR);

        GenerateMaterialsResponse.PreviewItem preview = null;
        QuizGenerationResult quizResult = null;
        try {
            preview = previewFuture.get(AI_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        } catch (Exception e) {
            log.error("预习作业生成超时或异常", e);
        }
        try {
            quizResult = quizFuture.get(AI_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        } catch (Exception e) {
            log.error("课堂试题生成超时或异常", e);
        }

        List<GenerateMaterialsResponse.QuizItem> quizzes = quizResult != null
                ? quizResult.getQuizzes() : List.of();
        String previewError = null;
        String quizError = null;

        if (preview == null) {
            previewError = "预习作业生成失败，请重试";
        }

        if (quizResult == null) {
            quizError = "课堂试题生成失败，请重试";
        }

        if (preview == null && quizzes.isEmpty()) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR.getCode(), "教学材料生成失败，请稍后重试");
        }

        return GenerateMaterialsResponse.builder()
                .preview(preview)
                .quizzes(quizzes)
                .pptFileName(doc.getDocName())
                .previewError(previewError)
                .quizError(quizError)
                .build();
    }

    private String truncateAtParagraph(String content, int maxChars) {
        if (content == null || content.length() <= maxChars) return content;
        int cut = content.lastIndexOf("\n\n", maxChars);
        if (cut < maxChars / 2) {
            cut = content.lastIndexOf('\n', maxChars);
        }
        if (cut < maxChars / 2) {
            cut = content.lastIndexOf('。', maxChars);
        }
        if (cut < maxChars / 2) {
            cut = maxChars;
        }
        log.info("PPT内容截断: {} -> {} 字符 (cut at {})", content.length(), cut, cut);
        return content.substring(0, cut);
    }

    public GenerateMaterialsResponse.PreviewItem savePreview(
            GenerateMaterialsResponse.PreviewItem item,
            Long classId,
            Long teacherId,
            String docId
    ) {
        PreviewTask task = PreviewTask.builder()
                .classId(classId)
                .teacherId(teacherId)
                .sourceDocId(docId)
                .title(item.getTopic())
                .knowledgePoint(item.getTopic())
                .guideText(item.getGuideText())
                .questionsJson(toJson(item.getQuestions()))
                .discussionQuestion(item.getDiscussionQuestion())
                .status("ACTIVE")
                .build();

        previewTaskMapper.insertWithJsonb(task);
        item.setSavedId(task.getId());
        item.setPublished(true);

        try {
            ClassInfo classInfo = classInfoMapper.selectById(classId);
            if (classInfo != null) {
                liveNotificationService.notifyPreviewTaskPublished(task, classInfo);
            }
        } catch (Exception e) {
            log.warn("预习任务QQ推送失败: taskId={}, {}", task.getId(), e.getMessage());
        }

        return item;
    }

    public GenerateMaterialsResponse.QuizItem saveQuiz(
            GenerateMaterialsResponse.QuizItem item,
            Long classId,
            String docId,
            Long teacherId
    ) {
        String optionsJson = item.getOptions() != null ? toJson(item.getOptions()) : null;
        Interaction interaction = Interaction.builder()
                .type(item.getType())
                .title(item.getTitle())
                .options(optionsJson)
                .correctKey(item.getCorrectKey())
                .knowledgePoint(item.getKnowledgePoint())
                .timeLimit(item.getTimeLimit())
                .status("DRAFT")
                .sortOrder(0)
                .aiGenerated(true)
                .sessionId(null)
                .classId(classId)
                .sourceDocId(docId)
                .build();

        interactionMapper.insertWithJsonb(interaction);
        item.setSavedId(interaction.getId());
        item.setPublished(true);
        saveQuizToQuestionBank(item, teacherId);
        return item;
    }

    private void saveQuizToQuestionBank(GenerateMaterialsResponse.QuizItem item, Long teacherId) {
        if (teacherId == null) return;

        LocalDateTime now = LocalDateTime.now();
        QuestionBankItem bankItem = new QuestionBankItem();
        bankItem.setTeacherId(teacherId);
        bankItem.setTitle(firstNonBlank(item.getTitle(), "AI生成题目"));
        bankItem.setRequirement(buildQuestionRequirement(item));
        bankItem.setScore(10);
        bankItem.setUploadRequired(false);
        bankItem.setCreatedAt(now);
        bankItem.setUpdatedAt(now);
        questionBankItemMapper.insert(bankItem);
    }

    private String buildQuestionRequirement(GenerateMaterialsResponse.QuizItem item) {
        StringBuilder text = new StringBuilder();
        appendLine(text, "类型", item.getType());
        appendLine(text, "知识点", item.getKnowledgePoint());

        if (item.getOptions() != null && !item.getOptions().isEmpty()) {
            text.append("选项:\n");
            for (GenerateMaterialsResponse.OptionItem option : item.getOptions()) {
                if (option == null) continue;
                String key = firstNonBlank(option.getKey(), "");
                String value = firstNonBlank(option.getText(), "");
                text.append(key);
                if (!key.isBlank()) text.append(". ");
                text.append(value).append('\n');
            }
        }

        appendLine(text, "答案", item.getCorrectKey());
        appendLine(text, "难度", item.getDifficulty());
        if (item.getTimeLimit() != null) {
            appendLine(text, "限时", item.getTimeLimit() + "s");
        }
        return text.toString().trim();
    }

    private void appendLine(StringBuilder text, String label, String value) {
        if (value == null || value.isBlank()) return;
        text.append(label).append(": ").append(value).append('\n');
    }

    private String firstNonBlank(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    private void validatePreview(GenerateMaterialsResponse.PreviewItem preview) {
        validateQuestions(preview.getQuestions(), "questions");
    }

    private void validateQuizzes(List<GenerateMaterialsResponse.QuizItem> quizzes) {
        List<String> violations = new ArrayList<>();
        for (int i = 0; i < quizzes.size(); i++) {
            validateChoiceAnswer(quizzes.get(i).getType(), quizzes.get(i).getOptions(),
                    quizzes.get(i).getCorrectKey(), "quizzes[" + i + "]", violations);
        }
        if (!violations.isEmpty()) throw new StructuredOutputValidationException(violations);
    }

    private void validateQuestions(List<GenerateMaterialsResponse.QuestionItem> questions, String path) {
        List<String> violations = new ArrayList<>();
        for (int i = 0; i < questions.size(); i++) {
            validateChoiceAnswer(questions.get(i).getType(), questions.get(i).getOptions(),
                    questions.get(i).getCorrectKey(), path + "[" + i + "]", violations);
        }
        if (!violations.isEmpty()) throw new StructuredOutputValidationException(violations);
    }

    private void validateChoiceAnswer(String type, List<GenerateMaterialsResponse.OptionItem> options,
                                      String correctKey, String path, List<String> violations) {
        if (!"CHOICE".equals(type)) return;
        if (options == null || options.size() < 2) {
            violations.add(path + ".options: choice requires at least two options");
            return;
        }
        if (options.stream().noneMatch(option -> option.getKey().equals(correctKey))) {
            violations.add(path + ".correctKey: must reference an option");
        }
    }

    private String toJson(Object obj) {
        try {
            return objectMapper.writeValueAsString(obj);
        } catch (Exception e) {
            return null;
        }
    }

    private static String joinFlux(Flux<String> flux) {
        return flux.collect(Collectors.joining()).block();
    }
}
