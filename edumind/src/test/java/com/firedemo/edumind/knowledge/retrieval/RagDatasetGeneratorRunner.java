package com.firedemo.edumind.knowledge.retrieval;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.firedemo.edumind.assistant.evaluation.model.EvalCase;
import com.firedemo.edumind.platform.json.JsonUtil;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/** Generates a stratified, evidence-bound retrieval dataset from indexed documents. */
class RagDatasetGeneratorRunner {

    private static final int MIN_ELIGIBLE_CHUNKS_PER_DOCUMENT = 30;
    private static final int MIN_CHUNK_CHARS = 80;
    private static final int MAX_EVIDENCE_SEGMENTS = 8;
    private static final int MAX_SEGMENT_CHARS = 500;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(30))
            .build();
    private final AtomicInteger apiCalls = new AtomicInteger();
    private final AtomicInteger rejectedInvalid = new AtomicInteger();
    private final AtomicInteger rejectedDuplicate = new AtomicInteger();

    @Test
    void generateEvidenceBoundDataset() throws Exception {
        Settings settings = Settings.load();
        JdbcTemplate jdbcTemplate = connect(settings);
        List<DocumentPool> documents = loadDocumentPools(jdbcTemplate);
        assertThat(documents).isNotEmpty();

        Map<String, Integer> quotas = allocateQuotas(documents, settings.targetCases());
        List<EvalCase> generated = loadCheckpoint(settings);
        List<String> acceptedQuestions = generated.stream().map(EvalCase::getQuery)
                .collect(Collectors.toCollection(ArrayList::new));
        Map<String, Integer> acceptedByDocument = new TreeMap<>();
        generated.forEach(value -> acceptedByDocument.merge(value.getSourceDocName(), 1, Integer::sum));
        Set<String> usedEvidence = generated.stream()
                .flatMap(value -> value.getExpectedContent().stream())
                .map(RagDatasetGeneratorRunner::normalize)
                .collect(Collectors.toCollection(HashSet::new));

        for (DocumentPool document : documents) {
            int quota = quotas.get(document.docId());
            List<ChunkCandidate> candidates = sampleCandidates(
                    document, Math.min(document.chunks().size(), quota * 8)).stream()
                    .filter(candidate -> candidate.evidence().stream()
                            .noneMatch(value -> usedEvidence.contains(normalize(value.text()))))
                    .toList();
            int cursor = 0;

            while (acceptedByDocument.getOrDefault(document.docName(), 0) < quota
                    && cursor < candidates.size()) {
                int end = Math.min(cursor + settings.batchSize(), candidates.size());
                List<ChunkCandidate> batch = candidates.subList(cursor, end);
                cursor = end;

                for (GeneratedItem item : generateBatch(settings, batch)) {
                    if (acceptedByDocument.getOrDefault(document.docName(), 0) >= quota) break;
                    acceptItem(item, generated, acceptedQuestions, usedEvidence, acceptedByDocument);
                }
            }

            int accepted = acceptedByDocument.getOrDefault(document.docName(), 0);
            System.out.printf("Generated %d/%d cases from %s%s%n",
                    accepted, quota, document.docName(), accepted < quota ? " (shortfall)" : "");
            writeCheckpoint(settings.outputDir(), generated, acceptedByDocument);
        }

        topUpToTarget(settings, documents, generated, acceptedQuestions, usedEvidence, acceptedByDocument);
        assertThat(generated).hasSize(settings.targetCases());
        validateDataset(generated);
        writeOutputs(settings, generated, documents, quotas, acceptedByDocument);
    }

    private JdbcTemplate connect(Settings settings) {
        DriverManagerDataSource dataSource = new DriverManagerDataSource();
        dataSource.setDriverClassName("org.postgresql.Driver");
        dataSource.setUrl(settings.dbUrl());
        dataSource.setUsername(settings.dbUser());
        dataSource.setPassword(settings.dbPassword());
        JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);
        assertThat(jdbcTemplate.queryForObject("SELECT 1", Integer.class)).isEqualTo(1);
        return jdbcTemplate;
    }

    private List<DocumentPool> loadDocumentPools(JdbcTemplate jdbcTemplate) {
        String sql = """
                SELECT id, doc_id, doc_name, chunk_index, content
                FROM document_chunk
                WHERE content IS NOT NULL AND length(content) >= ?
                ORDER BY doc_id, chunk_index, sub_index
                """;
        List<ChunkCandidate> queriedChunks = jdbcTemplate.query(sql, (rs, rowNum) -> {
            String chunkId = rs.getString("id");
            String sourceId = "SRC-" + chunkId;
            return new ChunkCandidate(
                    sourceId,
                    chunkId,
                    rs.getString("doc_id"),
                    rs.getString("doc_name"),
                    rs.getInt("chunk_index"),
                    rs.getString("content"),
                    splitEvidence(sourceId, rs.getString("content")));
        }, MIN_CHUNK_CHARS);
        List<ChunkCandidate> chunks = queriedChunks.stream()
                .filter(candidate -> !candidate.evidence().isEmpty())
                .toList();

        Map<String, List<ChunkCandidate>> grouped = chunks.stream()
                .collect(Collectors.groupingBy(
                        ChunkCandidate::docId, LinkedHashMap::new, Collectors.toList()));
        return grouped.values().stream()
                .filter(values -> values.size() >= MIN_ELIGIBLE_CHUNKS_PER_DOCUMENT)
                .map(values -> new DocumentPool(
                        values.getFirst().docId(), values.getFirst().docName(), List.copyOf(values)))
                .sorted(Comparator.comparingInt((DocumentPool pool) -> pool.chunks().size()).reversed())
                .toList();
    }

    private Map<String, Integer> allocateQuotas(List<DocumentPool> documents, int target) {
        assertThat(target).isGreaterThanOrEqualTo(documents.size());
        int base = Math.min(10, target / documents.size());
        int remaining = target - base * documents.size();
        int highCapacityDocuments = Math.min(10, documents.size());
        Map<String, Integer> quotas = new LinkedHashMap<>();
        for (DocumentPool document : documents) {
            quotas.put(document.docId(), base);
        }
        int index = 0;
        while (remaining > 0) {
            DocumentPool document = documents.get(index % highCapacityDocuments);
            quotas.merge(document.docId(), 1, Integer::sum);
            remaining--;
            index++;
        }
        return quotas;
    }

    private List<EvalCase> loadCheckpoint(Settings settings) throws Exception {
        Path checkpoint = settings.outputDir().resolve("rag-eval-dataset-200.checkpoint.json");
        if (!settings.resume() || !Files.exists(checkpoint)) return new ArrayList<>();
        try (var input = Files.newInputStream(checkpoint)) {
            List<EvalCase> cases = objectMapper.readValue(
                    input, new TypeReference<List<EvalCase>>() { });
            System.out.println("Resuming from checkpoint: " + cases.size() + " cases");
            return new ArrayList<>(cases);
        }
    }

    private List<ChunkCandidate> sampleCandidates(DocumentPool document, int sampleCount) {
        List<ChunkCandidate> source = document.chunks();
        if (sampleCount >= source.size()) return source;
        List<ChunkCandidate> sampled = new ArrayList<>();
        Set<Integer> used = new LinkedHashSet<>();
        for (int index = 0; index < sampleCount; index++) {
            int sourceIndex = Math.min(source.size() - 1,
                    (int) Math.floor((index + 0.5) * source.size() / sampleCount));
            if (used.add(sourceIndex)) sampled.add(source.get(sourceIndex));
        }
        return sampled;
    }

    private List<GeneratedItem> generateBatch(Settings settings,
                                               List<ChunkCandidate> batch) throws Exception {
        String contextJson = objectMapper.writerWithDefaultPrettyPrinter()
                .writeValueAsString(batch.stream().map(this::promptRecord).toList());
        String prompt = """
                请为 RAG 检索评测生成问题。输入包含若干彼此独立的证据记录，每条记录都有 sourceId 和带编号的 evidence。

                对每个 sourceId 恰好生成 1 道中文问题，并遵守：
                1. 任务是生成“用于找到该证据”的自然检索问题，不需要解答原知识题。
                2. 问题必须与该 sourceId 的 evidence 对应，不能引入 evidence 中不存在的主题。
                3. 问题要像学生自然提问，不要直接复制完整证据句，不要提“材料/原文/第几条”。
                4. evidenceIds 选择 1-2 个输入中真实存在的编号。
                5. keywords 给出 3-6 个确实出现在所选 evidence 中的关键词。
                6. 若证据明显被截断、只有考试时间/姓名/班级等元数据，或无法形成完整检索意图，设置 skip=true。
                7. 尽量覆盖定义、规则、区别、结果判断、代码语义和应用场景等不同问法。
                8. 只输出一个 JSON 对象，不要 Markdown：
                   {"items":[{"sourceId":"...","skip":false,"question":"...","evidenceIds":["..."],"keywords":["..."]}]}

                输入证据：
                """ + contextJson;

        Exception lastError = null;
        for (int attempt = 1; attempt <= 2; attempt++) {
            try {
                return parseGeneratedItems(callModel(settings, prompt), batch);
            } catch (Exception error) {
                lastError = error;
                if (attempt < 2) Thread.sleep(750L * attempt);
            }
        }
        throw new IllegalStateException("dataset generation batch failed", lastError);
    }

    private boolean acceptItem(GeneratedItem item,
                               List<EvalCase> generated,
                               List<String> acceptedQuestions,
                               Set<String> usedEvidence,
                               Map<String, Integer> acceptedByDocument) {
        if (isNearDuplicate(item.question(), acceptedQuestions)) {
            rejectedDuplicate.incrementAndGet();
            return false;
        }
        generated.add(EvalCase.builder()
                .id(generated.size() + 1)
                .query(item.question())
                .groundTruth(item.answer())
                .expectedKeywords(item.keywords())
                .expectedContent(item.evidenceTexts())
                .minChunksToCover(1)
                .sourceDocId(item.candidate().docId())
                .sourceDocName(item.candidate().docName())
                .build());
        acceptedQuestions.add(item.question());
        item.evidenceTexts().stream().map(RagDatasetGeneratorRunner::normalize)
                .forEach(usedEvidence::add);
        acceptedByDocument.merge(item.candidate().docName(), 1, Integer::sum);
        return true;
    }

    private void topUpToTarget(Settings settings,
                               List<DocumentPool> documents,
                               List<EvalCase> generated,
                               List<String> acceptedQuestions,
                               Set<String> usedEvidence,
                               Map<String, Integer> acceptedByDocument) throws Exception {
        if (generated.size() >= settings.targetCases()) return;
        System.out.printf("Starting global top-up: %d/%d%n", generated.size(), settings.targetCases());

        for (DocumentPool document : documents) {
            List<ChunkCandidate> candidates = sampleCandidates(
                    document, Math.min(document.chunks().size(), 240)).stream()
                    .filter(candidate -> candidate.evidence().stream()
                            .noneMatch(value -> usedEvidence.contains(normalize(value.text()))))
                    .toList();
            for (int cursor = 0; cursor < candidates.size(); cursor += settings.batchSize()) {
                int end = Math.min(cursor + settings.batchSize(), candidates.size());
                for (GeneratedItem item : generateBatch(settings, candidates.subList(cursor, end))) {
                    if (generated.size() >= settings.targetCases()) break;
                    acceptItem(item, generated, acceptedQuestions, usedEvidence, acceptedByDocument);
                }
                writeCheckpoint(settings.outputDir(), generated, acceptedByDocument);
                if (generated.size() >= settings.targetCases()) {
                    System.out.println("Global top-up complete: " + generated.size());
                    return;
                }
            }
        }
        throw new IllegalStateException(
                "Unable to reach target dataset size: " + generated.size() + "/" + settings.targetCases());
    }

    private Map<String, Object> promptRecord(ChunkCandidate candidate) {
        Map<String, Object> record = new LinkedHashMap<>();
        record.put("sourceId", candidate.sourceId());
        record.put("document", candidate.docName());
        record.put("evidence", candidate.evidence().stream().map(value -> {
            Map<String, String> item = new LinkedHashMap<>();
            item.put("id", value.id());
            item.put("text", value.text());
            return item;
        }).toList());
        return record;
    }

    private String callModel(Settings settings, String prompt) throws Exception {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", settings.llmModel());
        body.put("temperature", 0.2);
        body.put("max_tokens", 4096);
        body.put("thinking", Map.of("type", "disabled"));
        body.put("response_format", Map.of("type", "json_object"));
        body.put("messages", List.of(
                Map.of("role", "system", "content",
                        "你是严谨的数据集工程师，必须输出可由给定证据验证的 JSON。"),
                Map.of("role", "user", "content", prompt)));

        String baseUrl = settings.llmBaseUrl().replaceAll("/+$", "");
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/chat/completions"))
                .timeout(Duration.ofSeconds(75))
                .header("Authorization", "Bearer " + settings.llmApiKey())
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(
                        objectMapper.writeValueAsString(body), StandardCharsets.UTF_8))
                .build();
        apiCalls.incrementAndGet();
        HttpResponse<String> response = httpClient.send(
                request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new IllegalStateException(
                    "LLM HTTP " + response.statusCode() + ": " + truncate(response.body(), 500));
        }
        JsonNode root = objectMapper.readTree(response.body());
        String content = root.path("choices").path(0).path("message").path("content").asText();
        if (content.isBlank()) {
            String finishReason = root.path("choices").path(0).path("finish_reason").asText();
            throw new IllegalStateException("LLM returned empty content, finish_reason=" + finishReason);
        }
        return content;
    }

    private List<GeneratedItem> parseGeneratedItems(String response,
                                                    List<ChunkCandidate> batch) throws Exception {
        JsonNode root = objectMapper.readTree(JsonUtil.extractJson(response));
        JsonNode items = root.path("items");
        if (!items.isArray()) throw new IllegalArgumentException("items must be an array");

        Map<String, ChunkCandidate> bySource = batch.stream()
                .collect(Collectors.toMap(ChunkCandidate::sourceId, value -> value));
        Set<String> seenSources = new HashSet<>();
        List<GeneratedItem> accepted = new ArrayList<>();

        for (JsonNode item : items) {
            if (item.path("skip").asBoolean(false)) {
                rejectedInvalid.incrementAndGet();
                continue;
            }
            String sourceId = item.path("sourceId").asText().trim();
            String question = cleanText(item.path("question").asText());
            ChunkCandidate candidate = bySource.get(sourceId);
            if (candidate == null || !seenSources.add(sourceId)
                    || question.length() < 6) {
                rejectedInvalid.incrementAndGet();
                continue;
            }

            Map<String, Evidence> evidenceById = candidate.evidence().stream()
                    .collect(Collectors.toMap(Evidence::id, value -> value));
            List<Evidence> selectedEvidence = new ArrayList<>();
            for (JsonNode idNode : item.path("evidenceIds")) {
                Evidence evidence = evidenceById.get(idNode.asText());
                if (evidence != null && selectedEvidence.size() < 2) selectedEvidence.add(evidence);
            }
            if (selectedEvidence.isEmpty()) {
                rejectedInvalid.incrementAndGet();
                continue;
            }

            String normalizedEvidence = normalize(selectedEvidence.stream()
                    .map(Evidence::text).collect(Collectors.joining(" ")));
            List<String> keywords = new ArrayList<>();
            for (JsonNode keywordNode : item.path("keywords")) {
                String keyword = cleanText(keywordNode.asText());
                String normalizedKeyword = normalize(keyword);
                if (normalizedKeyword.length() >= 2
                        && normalizedEvidence.contains(normalizedKeyword)
                        && !keywords.contains(keyword)) {
                    keywords.add(keyword);
                }
            }
            if (keywords.size() < 2) {
                rejectedInvalid.incrementAndGet();
                continue;
            }

            accepted.add(new GeneratedItem(
                    candidate,
                    question,
                    selectedEvidence.stream().map(Evidence::text)
                            .collect(Collectors.joining("\n")),
                    List.copyOf(keywords),
                    selectedEvidence.stream().map(Evidence::text).toList()));
        }
        return accepted;
    }

    private static List<Evidence> splitEvidence(String sourceId, String content) {
        List<String> segments = new ArrayList<>();
        String normalizedLines = content == null ? "" : content.replace('\r', '\n');
        for (String part : normalizedLines.split("(?<=[。！？；.!?;])\\s*|\\n+")) {
            String clean = cleanText(part);
            if (clean.length() < 20) continue;
            if (clean.length() <= MAX_SEGMENT_CHARS && !isAdministrativeMetadata(clean)) {
                segments.add(clean);
            }
            if (segments.size() >= MAX_EVIDENCE_SEGMENTS) break;
        }
        if (segments.isEmpty()) {
            String clean = cleanText(content);
            if (clean.length() >= 20 && clean.length() <= MAX_SEGMENT_CHARS
                    && !isAdministrativeMetadata(clean)) {
                segments.add(clean);
            }
        }
        List<Evidence> evidence = new ArrayList<>();
        for (int index = 0; index < segments.size(); index++) {
            evidence.add(new Evidence(sourceId + "-E" + (index + 1), segments.get(index)));
        }
        return List.copyOf(evidence);
    }

    private boolean isNearDuplicate(String question, List<String> acceptedQuestions) {
        Set<String> candidate = bigrams(normalize(question));
        for (String accepted : acceptedQuestions) {
            if (jaccard(candidate, bigrams(normalize(accepted))) >= 0.82) return true;
        }
        return false;
    }

    private static Set<String> bigrams(String value) {
        Set<String> result = new HashSet<>();
        if (value.length() < 2) {
            if (!value.isBlank()) result.add(value);
            return result;
        }
        for (int index = 0; index < value.length() - 1; index++) {
            result.add(value.substring(index, index + 2));
        }
        return result;
    }

    private static double jaccard(Set<String> left, Set<String> right) {
        if (left.isEmpty() || right.isEmpty()) return 0;
        Set<String> intersection = new HashSet<>(left);
        intersection.retainAll(right);
        Set<String> union = new HashSet<>(left);
        union.addAll(right);
        return intersection.size() / (double) union.size();
    }

    private void validateDataset(List<EvalCase> cases) {
        assertThat(cases.stream().map(EvalCase::getQuery).distinct().count())
                .isEqualTo(cases.size());
        assertThat(cases).allSatisfy(evalCase -> {
            assertThat(evalCase.getSourceDocId()).isNotBlank();
            assertThat(evalCase.getExpectedContent()).isNotEmpty();
            assertThat(evalCase.getExpectedKeywords()).hasSizeGreaterThanOrEqualTo(2);
            assertThat(evalCase.getGroundTruth()).isNotBlank();
        });
    }

    private void writeOutputs(Settings settings,
                              List<EvalCase> cases,
                              List<DocumentPool> documents,
                              Map<String, Integer> quotas,
                              Map<String, Integer> acceptedByDocument) throws Exception {
        Files.createDirectories(settings.outputDir());
        Path datasetPath = settings.outputDir().resolve("rag-eval-dataset-200.json");
        Path reportPath = settings.outputDir().resolve("rag-eval-dataset-200-report.json");
        byte[] datasetBytes = objectMapper.writerWithDefaultPrettyPrinter().writeValueAsBytes(cases);
        Files.write(datasetPath, datasetBytes);

        Map<String, Object> report = new LinkedHashMap<>();
        report.put("generatedAt", Instant.now().toString());
        report.put("targetCases", settings.targetCases());
        report.put("actualCases", cases.size());
        report.put("eligibleDocuments", documents.size());
        report.put("apiCallsThisRun", apiCalls.get());
        report.put("resumedFromCheckpoint", settings.resume());
        report.put("rejectedInvalid", rejectedInvalid.get());
        report.put("rejectedNearDuplicate", rejectedDuplicate.get());
        report.put("datasetSha256", sha256(datasetBytes));
        report.put("llmModel", settings.llmModel());
        report.put("gitCommit", settings.gitCommit());
        report.put("quotasByDocId", quotas);
        report.put("acceptedByDocument", acceptedByDocument);
        report.put("evidenceBoundCases", cases.stream()
                .filter(value -> !value.getExpectedContent().isEmpty()).count());
        objectMapper.writerWithDefaultPrettyPrinter().writeValue(reportPath.toFile(), report);

        System.out.println("Dataset: " + datasetPath.toAbsolutePath());
        System.out.println("Report: " + reportPath.toAbsolutePath());
        System.out.println("Dataset SHA-256: " + report.get("datasetSha256"));
        System.out.printf("Generated=%d API calls=%d invalid=%d duplicates=%d%n",
                cases.size(), apiCalls.get(), rejectedInvalid.get(), rejectedDuplicate.get());
    }

    private void writeCheckpoint(Path outputDir,
                                 List<EvalCase> cases,
                                 Map<String, Integer> acceptedByDocument) throws Exception {
        Files.createDirectories(outputDir);
        objectMapper.writerWithDefaultPrettyPrinter().writeValue(
                outputDir.resolve("rag-eval-dataset-200.checkpoint.json").toFile(), cases);
        objectMapper.writerWithDefaultPrettyPrinter().writeValue(
                outputDir.resolve("rag-eval-dataset-200.checkpoint-progress.json").toFile(),
                acceptedByDocument);
    }

    private static String sha256(byte[] value) throws Exception {
        return java.util.HexFormat.of().withUpperCase()
                .formatHex(MessageDigest.getInstance("SHA-256").digest(value));
    }

    private static String cleanText(String value) {
        return value == null ? "" : value.replaceAll("\\s+", " ").trim();
    }

    private static String normalize(String value) {
        return cleanText(value).toLowerCase(Locale.ROOT)
                .replaceAll("[\\s，。！？、\\\"'（）《》：；,.!?;:()\\[\\]{}]", "");
    }

    private static String truncate(String value, int maxChars) {
        if (value == null) return "";
        return value.length() <= maxChars ? value : value.substring(0, maxChars) + "...";
    }

    private static boolean isAdministrativeMetadata(String value) {
        String normalized = normalize(value);
        int markers = 0;
        if (normalized.contains("考试时间")) markers++;
        if (normalized.contains("班级")) markers++;
        if (normalized.contains("学号")) markers++;
        if (normalized.contains("姓名")) markers++;
        return markers >= 2;
    }

    private record Evidence(String id, String text) { }

    private record ChunkCandidate(
            String sourceId,
            String chunkId,
            String docId,
            String docName,
            int chunkIndex,
            String content,
            List<Evidence> evidence) { }

    private record DocumentPool(String docId, String docName, List<ChunkCandidate> chunks) { }

    private record GeneratedItem(
            ChunkCandidate candidate,
            String question,
            String answer,
            List<String> keywords,
            List<String> evidenceTexts) { }

    private record Settings(
            String dbUrl,
            String dbUser,
            String dbPassword,
            String llmBaseUrl,
            String llmApiKey,
            String llmModel,
            Path outputDir,
            int targetCases,
            int batchSize,
            boolean resume,
            String gitCommit) {

        static Settings load() {
            return new Settings(
                    System.getProperty("rag.dataset.db-url",
                            "jdbc:postgresql://127.0.0.1:5432/postgres"),
                    System.getProperty("rag.dataset.db-user", "postgres"),
                    required("RAG_DATASET_DB_PASSWORD"),
                    required("RAG_DATASET_LLM_BASE_URL"),
                    required("RAG_DATASET_LLM_API_KEY"),
                    required("RAG_DATASET_LLM_MODEL"),
                    Path.of(System.getProperty("rag.dataset.output-dir", "target")),
                    Integer.getInteger("rag.dataset.target-cases", 200),
                    Integer.getInteger("rag.dataset.batch-size", 20),
                    Boolean.getBoolean("rag.dataset.resume"),
                    System.getProperty("rag.dataset.git-commit", "unknown"));
        }

        private static String required(String name) {
            String value = System.getenv(name);
            if (value == null || value.isBlank()) {
                throw new IllegalStateException("Missing environment variable: " + name);
            }
            return value;
        }
    }
}
