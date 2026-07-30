package com.firedemo.demo.Service;

import com.firedemo.demo.DTO.KnowledgeReclassificationResult;
import com.firedemo.demo.Entity.SubmissionError;
import com.firedemo.demo.Entity.TeacherKnowledge;
import com.firedemo.demo.infrastructure.ai.StructuredOutputInvoker;
import com.firedemo.demo.infrastructure.ai.StructuredOutputValidationException;
import com.firedemo.demo.mapper.SubmissionErrorMapper;
import com.firedemo.demo.mapper.TeacherKnowledgeMapper;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.CacheManager;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;

/** 真正通过 Spring 异步代理执行的历史错误重分类工作器。 */
@Slf4j
@Service
@RequiredArgsConstructor
public class KnowledgeReclassificationWorker {

    private static final int AI_BATCH_SIZE = 40;

    private final SubmissionErrorMapper submissionErrorMapper;
    private final TeacherKnowledgeMapper teacherKnowledgeMapper;
    private final OpenClawService openClawService;
    private final StructuredOutputInvoker structuredOutputInvoker;
    private final KnowledgeReclassificationTaskRegistry registry;
    private final CacheManager cacheManager;
    private final Map<String, String> conceptKeywords = new HashMap<>();

    @PostConstruct
    void loadConceptKeywords() {
        Properties properties = new Properties();
        try (InputStream input = getClass().getClassLoader()
                .getResourceAsStream("prompts/concept-keywords.properties")) {
            if (input == null) return;
            properties.load(new InputStreamReader(input, StandardCharsets.UTF_8));
            properties.forEach((key, value) -> conceptKeywords.put((String) key, (String) value));
        } catch (IOException | RuntimeException exception) {
            log.warn("Failed to load reclassification keyword mappings", exception);
        }
    }

    @Async
    public void execute(String taskId, Long classId) {
        int remainingOther = 0;
        try {
            List<SubmissionError> unclassified = submissionErrorMapper.selectUnclassifiedByClassId(classId);
            registry.markRunning(taskId, unclassified.size());
            if (unclassified.isEmpty()) {
                registry.complete(taskId, 0);
                return;
            }

            List<TeacherKnowledge> configured = teacherKnowledgeMapper.selectByClassId(classId);
            List<String> allowedNames = configured.stream()
                    .map(TeacherKnowledge::getName)
                    .filter(name -> name != null && !KnowledgePointVocabularyService.OTHER.equals(name))
                    .distinct()
                    .toList();
            if (allowedNames.isEmpty()) {
                registry.advance(taskId, unclassified.size(), 0, 0);
                registry.complete(taskId, unclassified.size());
                return;
            }

            Map<String, String> keywordMap = buildKeywordMap(allowedNames);
            List<SubmissionError> aiCandidates = new ArrayList<>();
            int keywordReclassified = 0;
            for (SubmissionError error : unclassified) {
                String matched = matchKeyword(error.getErrorText(), keywordMap);
                if (matched == null) {
                    aiCandidates.add(error);
                    continue;
                }
                error.setKnowledgePoint(matched);
                error.setUpdatedAt(LocalDateTime.now());
                submissionErrorMapper.updateById(error);
                keywordReclassified++;
            }
            registry.advance(taskId, keywordReclassified, keywordReclassified, 0);

            Set<String> allowed = new HashSet<>(allowedNames);
            allowed.add(KnowledgePointVocabularyService.OTHER);
            for (int offset = 0; offset < aiCandidates.size(); offset += AI_BATCH_SIZE) {
                List<SubmissionError> batch = aiCandidates.subList(
                        offset, Math.min(offset + AI_BATCH_SIZE, aiCandidates.size()));
                try {
                    KnowledgeReclassificationResult response = structuredOutputInvoker.invoke(
                            prompt -> openClawService.chat(prompt, "reclassify_" + classId + "_" + taskId),
                            buildPrompt(batch, allowedNames),
                            KnowledgeReclassificationResult.class,
                            "knowledge-reclassification",
                            result -> validate(result, batch.size(), allowed));

                    int changed = 0;
                    for (KnowledgeReclassificationResult.Item item : response.getResults()) {
                        if (KnowledgePointVocabularyService.OTHER.equals(item.getKnowledgePoint())) continue;
                        SubmissionError error = batch.get(item.getIndex());
                        error.setKnowledgePoint(item.getKnowledgePoint());
                        error.setUpdatedAt(LocalDateTime.now());
                        submissionErrorMapper.updateById(error);
                        changed++;
                    }
                    registry.advance(taskId, batch.size(), changed, 0);
                } catch (Exception batchFailure) {
                    registry.advance(taskId, batch.size(), 0, batch.size());
                    log.warn("AI reclassification batch failed: classId={}, taskId={}, offset={}",
                            classId, taskId, offset, batchFailure);
                }
            }

            remainingOther = submissionErrorMapper.countUnclassifiedByClassId(classId);
            evictDashboardCache(classId);
            registry.complete(taskId, remainingOther);
            log.info("Knowledge reclassification completed: classId={}, taskId={}, remainingOther={}",
                    classId, taskId, remainingOther);
        } catch (Exception exception) {
            try {
                remainingOther = submissionErrorMapper.countUnclassifiedByClassId(classId);
            } catch (Exception ignored) {
                // 保留最后一次已知值。
            }
            registry.fail(taskId, safeMessage(exception), remainingOther);
            log.error("Knowledge reclassification failed: classId={}, taskId={}", classId, taskId, exception);
        }
    }

    private Map<String, String> buildKeywordMap(List<String> allowedNames) {
        Map<String, String> result = new LinkedHashMap<>();
        Set<String> allowed = Set.copyOf(allowedNames);
        conceptKeywords.forEach((keyword, name) -> {
            if (allowed.contains(name)) result.put(keyword, name);
        });
        allowedNames.forEach(name -> result.putIfAbsent(name, name));
        return result;
    }

    private String matchKeyword(String errorText, Map<String, String> keywordMap) {
        if (errorText == null) return null;
        String lower = errorText.toLowerCase();
        return keywordMap.entrySet().stream()
                .filter(entry -> lower.contains(entry.getKey().toLowerCase()))
                .map(Map.Entry::getValue)
                .findFirst()
                .orElse(null);
    }

    private String buildPrompt(List<SubmissionError> errors, List<String> allowedNames) {
        StringBuilder prompt = new StringBuilder("请将每条错误归类到教师规定的知识点之一。\n\n错误列表：\n");
        for (int index = 0; index < errors.size(); index++) {
            prompt.append(index).append(": \"").append(errors.get(index).getErrorText()).append("\"\n");
        }
        prompt.append("\n允许的知识点：").append(allowedNames).append("\n")
                .append("只能返回以上精确名称；无法匹配时返回\"其他\"。\n")
                .append("返回纯 JSON：{\"results\":[{\"index\":0,\"knowledgePoint\":\"其他\"}]}\n")
                .append("必须为每个输入索引返回且只返回一条结果，不要输出解释。");
        return prompt.toString();
    }

    private void validate(KnowledgeReclassificationResult result, int errorCount, Set<String> allowed) {
        List<String> violations = new ArrayList<>();
        if (result == null || result.getResults() == null) {
            throw new StructuredOutputValidationException(List.of("results: required"));
        }
        if (result.getResults().size() != errorCount) {
            violations.add("results: expected " + errorCount + " items");
        }
        Set<Integer> indexes = new HashSet<>();
        for (int position = 0; position < result.getResults().size(); position++) {
            KnowledgeReclassificationResult.Item item = result.getResults().get(position);
            if (item.getIndex() < 0 || item.getIndex() >= errorCount) {
                violations.add("results[" + position + "].index: out of range");
            } else if (!indexes.add(item.getIndex())) {
                violations.add("results[" + position + "].index: duplicate");
            }
            if (!allowed.contains(item.getKnowledgePoint())) {
                violations.add("results[" + position + "].knowledgePoint: not allowed");
            }
        }
        if (indexes.size() != errorCount) violations.add("results: missing indexes");
        if (!violations.isEmpty()) throw new StructuredOutputValidationException(violations);
    }

    private void evictDashboardCache(Long classId) {
        var cache = cacheManager.getCache("dashboard");
        if (cache == null) return;
        cache.evict("knowledge:" + classId);
        cache.evict("errors:" + classId);
        cache.evict("students:" + classId);
    }

    private String safeMessage(Exception exception) {
        String message = exception.getMessage();
        if (message == null || message.isBlank()) return exception.getClass().getSimpleName();
        return message.length() <= 240 ? message : message.substring(0, 240);
    }
}
