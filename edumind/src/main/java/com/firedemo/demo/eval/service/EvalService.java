package com.firedemo.demo.eval.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.firedemo.demo.Entity.DocumentChunkEntity;
import com.firedemo.demo.Service.OpenClawService;
import com.firedemo.demo.config.properties.LlmProperties;
import com.firedemo.demo.eval.config.EvalProperties;
import com.firedemo.demo.eval.model.EvalCase;
import com.firedemo.demo.eval.model.EvalCaseResult;
import com.firedemo.demo.eval.model.EvalDatasetGenerationResult;
import com.firedemo.demo.eval.model.EvalResponse;
import com.firedemo.demo.eval.model.EvalRunConfig;
import com.firedemo.demo.eval.model.EvalRunRequest;
import com.firedemo.demo.eval.model.JudgeResult;
import com.firedemo.demo.eval.persistence.EvalRunStore;
import com.firedemo.demo.eval.persistence.EvalRunEntity;
import com.firedemo.demo.eval.model.EvalRunDetails;
import com.firedemo.demo.infrastructure.ai.StructuredOutputInvoker;
import com.firedemo.demo.mapper.DocumentChunkMapper;
import com.firedemo.demo.rag.RagResult;
import com.firedemo.demo.rag.RagSearchRequest;
import com.firedemo.demo.rag.RagService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;
import org.springframework.util.StreamUtils;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.ToDoubleFunction;
import java.util.stream.Collectors;

/** Reproducible RAG evaluation with persisted per-case evidence. */
@Slf4j
@Service
public class EvalService {

    private static final String QA_GEN_PROMPT = """
            你是教育领域的测试题生成专家。请根据以下文档内容，生成 %d 个不同角度的问题。

            要求：
            1. 问题覆盖文档不同部分，不要集中在一处
            2. 类型多样化：定义类、概念对比类、应用类、因果类
            3. 每个问题配完整参考答案（答案必须能从文档中找到依据）
            4. 输出严格 JSON 对象，不要加 markdown 标记：
            {"items":[{"question": "...", "answer": "..."}]}

            文档内容：
            %s

            JSON 输出：""";

    private final RagService ragService;
    private final OpenClawService openClawService;
    private final DocumentChunkMapper chunkMapper;
    private final ObjectMapper objectMapper;
    private final StructuredOutputInvoker structuredOutputInvoker;
    private final RetrievalMetricsCalculator retrievalMetricsCalculator;
    private final EvalRunStore runStore;
    private final EvalMetricsPublisher metricsPublisher;
    private final EvalProperties properties;
    private final LlmProperties llmProperties;

    private String faithfulnessPrompt;
    private String relevancePrompt;

    public EvalService(RagService ragService,
                       OpenClawService openClawService,
                       DocumentChunkMapper chunkMapper,
                       ObjectMapper objectMapper,
                       StructuredOutputInvoker structuredOutputInvoker,
                       RetrievalMetricsCalculator retrievalMetricsCalculator,
                       EvalRunStore runStore,
                       EvalMetricsPublisher metricsPublisher,
                       EvalProperties properties,
                       LlmProperties llmProperties) {
        this.ragService = ragService;
        this.openClawService = openClawService;
        this.chunkMapper = chunkMapper;
        this.objectMapper = objectMapper;
        this.structuredOutputInvoker = structuredOutputInvoker;
        this.retrievalMetricsCalculator = retrievalMetricsCalculator;
        this.runStore = runStore;
        this.metricsPublisher = metricsPublisher;
        this.properties = properties;
        this.llmProperties = llmProperties;
        loadPrompts();
    }

    private void loadPrompts() {
        try {
            faithfulnessPrompt = loadResource("prompts/judge-faithfulness.txt");
            relevancePrompt = loadResource("prompts/judge-relevance.txt");
        } catch (Exception e) {
            log.warn("加载 Judge prompt 失败: {}", e.getMessage());
        }
    }

    private String loadResource(String path) throws Exception {
        try (InputStream in = new ClassPathResource(path).getInputStream()) {
            return StreamUtils.copyToString(in, StandardCharsets.UTF_8);
        }
    }

    public List<EvalCase> generateDatasetFromDb(List<String> docIds, int questionCount) {
        LambdaQueryWrapper<DocumentChunkEntity> query = new LambdaQueryWrapper<>();
        query.isNotNull(DocumentChunkEntity::getContent).ne(DocumentChunkEntity::getContent, "");
        if (docIds != null && !docIds.isEmpty()) {
            query.in(DocumentChunkEntity::getDocumentId, docIds);
        }

        List<DocumentChunkEntity> allChunks = chunkMapper.selectList(query);
        if (allChunks.isEmpty()) {
            log.warn("数据库中没有已索引的文档");
            return List.of();
        }

        Map<String, List<DocumentChunkEntity>> byDocument = allChunks.stream()
                .collect(Collectors.groupingBy(DocumentChunkEntity::getDocumentId,
                        java.util.TreeMap::new, Collectors.toList()));
        List<EvalCase> cases = new ArrayList<>();
        int caseId = 0;

        for (Map.Entry<String, List<DocumentChunkEntity>> entry : byDocument.entrySet()) {
            List<DocumentChunkEntity> chunks = entry.getValue();
            chunks.sort(Comparator.comparing(DocumentChunkEntity::getSectionIndex,
                    Comparator.nullsLast(Comparator.naturalOrder())));
            String content = chunks.stream().map(DocumentChunkEntity::getContent)
                    .collect(Collectors.joining("\n"));
            if (content.length() > 15_000) {
                content = content.substring(0, 7_000) + "\n...(中间内容已截断)...\n"
                        + content.substring(content.length() - 7_000);
            }

            try {
                String prompt = String.format(QA_GEN_PROMPT, questionCount, content);
                EvalDatasetGenerationResult generated = structuredOutputInvoker.invoke(
                        value -> openClawService.chat(value, "eval-dataset-gen"), prompt,
                        EvalDatasetGenerationResult.class, "eval-dataset-generation");
                for (EvalDatasetGenerationResult.Item item : generated.getItems()) {
                    cases.add(EvalCase.builder()
                            .id(++caseId)
                            .query(item.getQuestion().trim())
                            .groundTruth(item.getAnswer().trim())
                            .sourceDocId(entry.getKey())
                            .sourceDocName(chunks.getFirst().getDocumentName())
                            .build());
                }
            } catch (RuntimeException e) {
                log.warn("评测数据生成失败: docId={}, error={}", entry.getKey(), e.getMessage());
            }
        }
        return cases;
    }

    /** Backward-compatible entry point used by the existing evaluation runner. */
    public EvalResponse runEvaluation(List<String> metrics, int topK,
                                      boolean enableReranker, boolean generateAnswers) {
        EvalRunRequest request = new EvalRunRequest();
        request.setMetrics(metrics);
        request.setTopK(topK);
        request.setEnableReranker(enableReranker);
        request.setGenerateAnswers(generateAnswers);
        return runEvaluation(request);
    }

    public EvalResponse runEvaluation(EvalRunRequest request) {
        long startedAt = System.currentTimeMillis();
        List<EvalCase> cases = loadDataset();
        if (cases.isEmpty()) cases = generateDatasetFromDb(null, 3);
        if (cases.isEmpty()) {
            return EvalResponse.builder().status("error")
                    .error("评测数据集为空且数据库无已索引文档").build();
        }

        EvalRunConfig config = createConfig(request, cases);
        Long runId = runStore.start(config);
        List<EvalCaseResult> results = new ArrayList<>();

        try {
            for (EvalCase evalCase : cases) {
                EvalCaseResult result;
                try {
                    result = evaluateCase(evalCase, request);
                } catch (RuntimeException caseError) {
                    log.warn("Evaluation case failed: runId={}, caseId={}, error={}",
                            runId, evalCase.getId(), caseError.getMessage());
                    result = failedCase(evalCase, caseError);
                }
                results.add(result);
                runStore.saveCase(runId, result);
            }

            Map<String, Double> summary = summarize(results);
            List<String> gateFailures = evaluateQualityGates(summary, request);
            boolean gatePassed = gateFailures.isEmpty();
            long durationMs = System.currentTimeMillis() - startedAt;

            runStore.complete(runId, summary, results.size(), durationMs, gatePassed);
            metricsPublisher.publish(summary, gatePassed);
            logSummary(runId, results.size(), summary, gateFailures, durationMs);

            return EvalResponse.builder()
                    .status("ok")
                    .runId(runId)
                    .config(config)
                    .summary(summary)
                    .perCase(List.copyOf(results))
                    .numCases(results.size())
                    .evalDurationSeconds(durationMs / 1_000.0)
                    .qualityGatePassed(gatePassed)
                    .qualityGateFailures(gateFailures)
                    .build();
        } catch (RuntimeException e) {
            runStore.fail(runId, e);
            metricsPublisher.publishFailure();
            log.error("RAG evaluation failed: runId={}", runId, e);
            return EvalResponse.builder().status("error").runId(runId).config(config)
                    .error(e.getMessage()).build();
        }
    }

    public List<EvalRunEntity> listRuns(int limit) {
        return runStore.listRecent(limit);
    }

    public EvalRunDetails getRun(Long runId) {
        return runStore.getDetails(runId);
    }

    private EvalCaseResult evaluateCase(EvalCase evalCase, EvalRunRequest request) {
        long retrievalStarted = System.currentTimeMillis();
        RagResult ragResult = ragService.search(RagSearchRequest.builder()
                .query(evalCase.getQuery())
                .topK(request.getTopK())
                .enableReranker(request.isEnableReranker())
                .build());
        long retrievalMs = System.currentTimeMillis() - retrievalStarted;

        RetrievalMetricsCalculator.Metrics retrieval = retrievalMetricsCalculator
                .calculate(evalCase, ragResult.getResults());
        List<String> contexts = ragResult.getResults().stream()
                .map(result -> result.chunk().getContent()).toList();
        String referenceAnswer = firstNonBlank(evalCase.getGroundTruth(), evalCase.getAnswer());

        long generationStarted = System.currentTimeMillis();
        String generatedAnswer = request.isGenerateAnswers()
                ? (ragResult.isHasContext() ? generateAnswer(evalCase.getQuery(), contexts) : "")
                : "";
        long generationMs = System.currentTimeMillis() - generationStarted;

        Boolean faithfulness = null;
        if (request.getMetrics().contains("faithfulness")
                && !contexts.isEmpty() && hasText(generatedAnswer)) {
            faithfulness = judgeFaithfulness(evalCase.getQuery(), generatedAnswer,
                    String.join("\n---\n", contexts));
        }

        Boolean relevancy = null;
        if (request.getMetrics().contains("answer_relevancy") && hasText(generatedAnswer)) {
            relevancy = judgeRelevancy(evalCase.getQuery(), generatedAnswer);
        }

        return EvalCaseResult.builder()
                .caseId(evalCase.getId())
                .query(evalCase.getQuery())
                .sourceDocId(evalCase.getSourceDocId())
                .referenceAnswer(referenceAnswer)
                .generatedAnswer(generatedAnswer)
                .retrievedChunkIds(ragResult.getResults().stream()
                        .map(result -> result.chunk().getId()).toList())
                .keywordRecall(retrieval.keywordRecall())
                .contentCoverage(retrieval.contentCoverage())
                .reciprocalRank(retrieval.reciprocalRank())
                .ndcg(retrieval.ndcg())
                .retrievalHit(retrieval.hit())
                .faithfulness(faithfulness)
                .answerRelevancy(relevancy)
                .retrievalMs(retrievalMs)
                .generationMs(generationMs)
                .build();
    }

    private EvalCaseResult failedCase(EvalCase evalCase, RuntimeException error) {
        return EvalCaseResult.builder()
                .caseId(evalCase.getId())
                .query(evalCase.getQuery())
                .sourceDocId(evalCase.getSourceDocId())
                .referenceAnswer(firstNonBlank(evalCase.getGroundTruth(), evalCase.getAnswer()))
                .generatedAnswer("")
                .retrievedChunkIds(List.of())
                .keywordRecall(-1)
                .contentCoverage(-1)
                .reciprocalRank(0)
                .ndcg(0)
                .retrievalHit(false)
                .retrievalMs(0)
                .generationMs(0)
                .error(truncate(nullToEmpty(error.getMessage()), 2_000))
                .build();
    }

    private Map<String, Double> summarize(List<EvalCaseResult> results) {
        Map<String, Double> summary = new LinkedHashMap<>();
        putAverage(summary, "keyword_recall", results, EvalCaseResult::keywordRecall, true);
        putAverage(summary, "content_coverage", results, EvalCaseResult::contentCoverage, true);
        putAverage(summary, "mrr", results, EvalCaseResult::reciprocalRank, false);
        putAverage(summary, "ndcg", results, EvalCaseResult::ndcg, false);
        summary.put("hit_rate", results.stream().filter(EvalCaseResult::retrievalHit).count()
                / (double) results.size());

        averageBoolean(results.stream().map(EvalCaseResult::faithfulness).toList())
                .ifPresent(value -> summary.put("faithfulness", value));
        averageBoolean(results.stream().map(EvalCaseResult::answerRelevancy).toList())
                .ifPresent(value -> summary.put("answer_relevancy", value));
        summary.put("case_error_rate", results.stream().filter(result -> result.error() != null).count()
                / (double) results.size());
        summary.put("avg_retrieval_ms", results.stream().mapToLong(EvalCaseResult::retrievalMs)
                .average().orElse(0));
        summary.put("avg_generation_ms", results.stream().mapToLong(EvalCaseResult::generationMs)
                .average().orElse(0));
        return Map.copyOf(summary);
    }

    private void putAverage(Map<String, Double> summary, String key, List<EvalCaseResult> results,
                            ToDoubleFunction<EvalCaseResult> extractor, boolean skipUnavailable) {
        double value = results.stream().mapToDouble(extractor)
                .filter(metric -> !skipUnavailable || metric >= 0)
                .average().orElse(-1);
        if (value >= 0) summary.put(key, value);
    }

    private java.util.OptionalDouble averageBoolean(List<Boolean> values) {
        return values.stream().filter(Objects::nonNull)
                .mapToDouble(value -> value ? 1 : 0).average();
    }

    private List<String> evaluateQualityGates(Map<String, Double> summary, EvalRunRequest request) {
        Map<String, Double> thresholds = effectiveThresholds(request);
        List<String> failures = new ArrayList<>();
        for (Map.Entry<String, Double> threshold : thresholds.entrySet()) {
            if (threshold.getValue() < 0 || threshold.getValue() > 1) {
                throw new IllegalArgumentException("Quality threshold must be between 0 and 1: " + threshold.getKey());
            }
            if (!request.getMetrics().contains(threshold.getKey())) continue;
            Double actual = summary.get(threshold.getKey());
            if (actual == null) {
                failures.add(threshold.getKey() + " unavailable");
            } else if (actual < threshold.getValue()) {
                failures.add(String.format("%s %.3f < %.3f", threshold.getKey(), actual, threshold.getValue()));
            }
        }
        if (summary.getOrDefault("case_error_rate", 0.0) > 0) {
            failures.add(String.format("case_error_rate %.3f > 0", summary.get("case_error_rate")));
        }
        return List.copyOf(failures);
    }

    private Map<String, Double> defaultThresholds() {
        return Map.of(
                "keyword_recall", properties.getMinKeywordRecall(),
                "content_coverage", properties.getMinContentCoverage(),
                "hit_rate", properties.getMinHitRate(),
                "mrr", properties.getMinMrr(),
                "ndcg", properties.getMinNdcg(),
                "faithfulness", properties.getMinFaithfulness(),
                "answer_relevancy", properties.getMinAnswerRelevancy());
    }

    private Map<String, Double> effectiveThresholds(EvalRunRequest request) {
        Map<String, Double> thresholds = new LinkedHashMap<>(defaultThresholds());
        if (request.getThresholds() != null) {
            request.getThresholds().forEach((metric, value) -> {
                if (!thresholds.containsKey(metric)) {
                    throw new IllegalArgumentException("Unknown quality metric threshold: " + metric);
                }
                if (value == null || value < 0 || value > 1) {
                    throw new IllegalArgumentException(
                            "Quality threshold must be between 0 and 1: " + metric);
                }
                thresholds.put(metric, value);
            });
        }
        return Map.copyOf(thresholds);
    }

    private EvalRunConfig createConfig(EvalRunRequest request, List<EvalCase> cases) {
        return EvalRunConfig.builder()
                .datasetVersion(firstNonBlank(request.getDatasetVersion(), properties.getDatasetVersion()))
                .datasetHash(datasetHash(cases))
                .gitCommit(properties.getGitCommit())
                .llmModel(llmProperties.resolveTextModel())
                .embeddingModel(properties.getEmbeddingModel())
                .rerankerModel(properties.getRerankerModel())
                .topK(request.getTopK())
                .rerankerEnabled(request.isEnableReranker())
                .generateAnswers(request.isGenerateAnswers())
                .metrics(request.getMetrics())
                .thresholds(effectiveThresholds(request))
                .build();
    }

    private String datasetHash(List<EvalCase> cases) {
        try {
            byte[] canonical = objectMapper.writeValueAsBytes(cases);
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(canonical));
        } catch (JsonProcessingException | NoSuchAlgorithmException e) {
            throw new IllegalStateException("Unable to fingerprint evaluation dataset", e);
        }
    }

    boolean judgeFaithfulness(String query, String answer, String context) {
        return callJudge(faithfulnessPrompt
                .replace("{{query}}", query)
                .replace("{{answer}}", answer)
                .replace("{{context}}", truncate(context, 3_000)));
    }

    boolean judgeRelevancy(String query, String answer) {
        return callJudge(relevancePrompt.replace("{{query}}", query).replace("{{answer}}", answer));
    }

    private boolean callJudge(String prompt) {
        JudgeResult result = structuredOutputInvoker.invoke(
                value -> openClawService.chat(value, "eval-judge"), prompt,
                JudgeResult.class, "eval-judge");
        return result.getScore() == 1;
    }

    private String generateAnswer(String query, List<String> contexts) {
        StringBuilder prompt = new StringBuilder("请根据以下参考资料回答问题。\n\n参考资料：\n");
        for (int i = 0; i < Math.min(contexts.size(), 3); i++) {
            prompt.append("---\n").append(contexts.get(i)).append('\n');
        }
        prompt.append("\n问题：").append(query).append("\n\n请直接回答：");
        return nullToEmpty(openClawService.chat(prompt.toString(), "eval-gen"));
    }

    List<EvalCase> loadDataset() {
        try (InputStream in = new ClassPathResource("rag-eval-dataset.json").getInputStream()) {
            return objectMapper.readValue(in, new TypeReference<List<EvalCase>>() {});
        } catch (Exception e) {
            return List.of();
        }
    }

    private void logSummary(Long runId, int numCases, Map<String, Double> summary,
                            List<String> gateFailures, long durationMs) {
        log.info("RAG evaluation completed: runId={}, cases={}, durationMs={}, scores={}, gatePassed={}, failures={}",
                runId, numCases, durationMs, summary, gateFailures.isEmpty(), gateFailures);
    }

    private static String truncate(String value, int max) {
        return value.length() <= max ? value : value.substring(0, max) + "\n...(截断)";
    }

    private static String firstNonBlank(String primary, String fallback) {
        return hasText(primary) ? primary : fallback;
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
