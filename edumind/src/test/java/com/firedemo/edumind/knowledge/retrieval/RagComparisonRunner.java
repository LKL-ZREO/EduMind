package com.firedemo.edumind.knowledge.retrieval;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.firedemo.edumind.assistant.evaluation.model.EvalCase;
import com.firedemo.edumind.assistant.evaluation.service.RetrievalMetricsCalculator;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Local, reproducible retrieval comparison that does not start Spring Boot.
 *
 * <p>It compares vector-only retrieval with vector + keyword + RRF + reranker,
 * using the same PostgreSQL corpus, embedding model, Top-K, and evaluation set.
 * Answer generation and query rewriting are intentionally excluded.</p>
 */
class RagComparisonRunner {

    private static final int TOP_K = 5;
    private static final int CANDIDATE_K = TOP_K * 3;
    private static final int DEFAULT_REPEATS = 5;
    private static final int DEFAULT_WARMUP_CASES = 3;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final RetrievalMetricsCalculator metricsCalculator = new RetrievalMetricsCalculator();
    private final RrfFusionService rrfFusionService = new RrfFusionService();

    private EmbeddingService embeddingService;
    private RerankerService rerankerService;
    private VectorStoreService vectorStoreService;

    @Test
    void compareVectorOnlyWithHybridRerank() throws Exception {
        Settings settings = Settings.load();
        List<EvalCase> cases = loadCases(settings);
        assertThat(cases).as("evaluation dataset must contain cases").isNotEmpty();

        JdbcTemplate jdbcTemplate = connect(settings);
        Map<String, Object> corpus = inspectCorpus(jdbcTemplate);
        initializeRetrievalServices(settings, jdbcTemplate);

        assertThat(rerankerService.isModelReady())
                .as("reranker model must be ready at %s", settings.rerankerModelDir())
                .isTrue();

        warmUp(cases, settings.warmupCases());

        List<Long> vectorLatencies = new ArrayList<>();
        List<Long> hybridLatencies = new ArrayList<>();
        List<PipelineResult> vectorQuality = new ArrayList<>();
        List<PipelineResult> hybridQuality = new ArrayList<>();

        for (int repeat = 0; repeat < settings.repeats(); repeat++) {
            for (int index = 0; index < cases.size(); index++) {
                EvalCase evalCase = cases.get(index);
                boolean vectorFirst = ((repeat + index) & 1) == 0;

                PipelineResult vector;
                PipelineResult hybrid;
                if (vectorFirst) {
                    vector = runVectorOnly(evalCase);
                    hybrid = runHybridRerank(evalCase);
                } else {
                    hybrid = runHybridRerank(evalCase);
                    vector = runVectorOnly(evalCase);
                }

                vectorLatencies.add(vector.elapsedMs());
                hybridLatencies.add(hybrid.elapsedMs());
                if (repeat == 0) {
                    vectorQuality.add(vector);
                    hybridQuality.add(hybrid);
                }

                System.out.printf(Locale.ROOT,
                        "repeat=%d/%d case=%d/%d vector=%dms hybrid=%dms vectorHit=%s hybridHit=%s%n",
                        repeat + 1, settings.repeats(), index + 1, cases.size(),
                        vector.elapsedMs(), hybrid.elapsedMs(),
                        vector.metrics().hit(), hybrid.metrics().hit());
            }
        }

        QualitySummary vectorSummary = QualitySummary.from(vectorQuality);
        QualitySummary hybridSummary = QualitySummary.from(hybridQuality);
        LatencySummary vectorLatency = LatencySummary.from(vectorLatencies);
        LatencySummary hybridLatency = LatencySummary.from(hybridLatencies);

        Map<String, Object> report = buildReport(
                settings, cases, corpus,
                vectorSummary, hybridSummary,
                vectorLatency, hybridLatency,
                vectorQuality, hybridQuality);
        writeOutputs(settings.outputDir(), report, vectorQuality, hybridQuality);

        printSummary(vectorSummary, hybridSummary, vectorLatency, hybridLatency, settings);
    }

    private JdbcTemplate connect(Settings settings) {
        DriverManagerDataSource dataSource = new DriverManagerDataSource();
        dataSource.setDriverClassName("org.postgresql.Driver");
        dataSource.setUrl(settings.dbUrl());
        dataSource.setUsername(settings.dbUser());
        dataSource.setPassword(settings.dbPassword());
        JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);
        Integer connected = jdbcTemplate.queryForObject("SELECT 1", Integer.class);
        assertThat(connected).isEqualTo(1);
        return jdbcTemplate;
    }

    private Map<String, Object> inspectCorpus(JdbcTemplate jdbcTemplate) {
        Map<String, Object> corpus = new LinkedHashMap<>();
        corpus.put("chunkCount", jdbcTemplate.queryForObject(
                "SELECT count(*) FROM document_chunk", Long.class));
        corpus.put("embeddedChunkCount", jdbcTemplate.queryForObject(
                "SELECT count(*) FROM document_chunk WHERE embedding_vec IS NOT NULL", Long.class));
        corpus.put("documentCount", jdbcTemplate.queryForObject(
                "SELECT count(DISTINCT doc_id) FROM document_chunk", Long.class));
        corpus.put("pgvectorVersion", jdbcTemplate.queryForObject(
                "SELECT extversion FROM pg_extension WHERE extname = 'vector'", String.class));
        return corpus;
    }

    private void initializeRetrievalServices(Settings settings, JdbcTemplate jdbcTemplate) {
        embeddingService = new EmbeddingService();
        embeddingService.init();

        rerankerService = new RerankerService();
        ReflectionTestUtils.setField(rerankerService, "modelDir", settings.rerankerModelDir());
        rerankerService.init();

        vectorStoreService = new VectorStoreService();
        ReflectionTestUtils.setField(vectorStoreService, "jdbcTemplate", jdbcTemplate);
        vectorStoreService.init();
    }

    private void warmUp(List<EvalCase> cases, int warmupCases) {
        int count = Math.min(warmupCases, cases.size());
        for (int index = 0; index < count; index++) {
            EvalCase evalCase = cases.get(index);
            runVectorOnly(evalCase);
            runHybridRerank(evalCase);
        }
        System.out.printf("Warm-up complete: %d cases per mode%n", count);
    }

    private PipelineResult runVectorOnly(EvalCase evalCase) {
        long started = System.nanoTime();
        float[] queryEmbedding = embeddingService.embedQuery(evalCase.getQuery());
        List<DocumentChunk> chunks = vectorStoreService.similaritySearch(
                queryEmbedding, TOP_K, null, Set.of());
        List<RrfFusionService.ScoredChunk> ranked = rankOnly(chunks);
        long elapsedMs = elapsedMs(started);
        return result(evalCase, ranked, elapsedMs);
    }

    private PipelineResult runHybridRerank(EvalCase evalCase) {
        long started = System.nanoTime();
        float[] queryEmbedding = embeddingService.embedQuery(evalCase.getQuery());
        List<DocumentChunk> vectorChunks = vectorStoreService.similaritySearch(
                queryEmbedding, CANDIDATE_K, null, Set.of());
        List<DocumentChunk> keywordChunks = vectorStoreService.keywordSearch(
                        evalCase.getQuery(), CANDIDATE_K, null, Set.of()).stream()
                .map(VectorStoreService.ScoredChunk::chunk)
                .toList();
        List<RrfFusionService.ScoredChunk> fused = rrfFusionService.fuse(vectorChunks, keywordChunks);
        List<RrfFusionService.ScoredChunk> reranked = rerankerService.rerank(
                evalCase.getQuery(), fused, TOP_K, null);
        long elapsedMs = elapsedMs(started);
        return result(evalCase, reranked, elapsedMs);
    }

    private List<RrfFusionService.ScoredChunk> rankOnly(List<DocumentChunk> chunks) {
        List<RrfFusionService.ScoredChunk> ranked = new ArrayList<>();
        for (int index = 0; index < chunks.size(); index++) {
            ranked.add(new RrfFusionService.ScoredChunk(chunks.get(index), 1.0 / (index + 1)));
        }
        return ranked;
    }

    private PipelineResult result(EvalCase evalCase,
                                  List<RrfFusionService.ScoredChunk> chunks,
                                  long elapsedMs) {
        RetrievalMetricsCalculator.Metrics metrics = metricsCalculator.calculate(evalCase, chunks);
        List<String> chunkIds = chunks.stream().map(result -> result.chunk().getId()).toList();
        List<String> documentNames = chunks.stream()
                .map(result -> result.chunk().getDocumentName())
                .map(name -> name == null ? "<unknown>" : name)
                .toList();
        return new PipelineResult(evalCase, metrics, elapsedMs, chunkIds, documentNames);
    }

    private List<EvalCase> loadCases(Settings settings) throws Exception {
        if (settings.datasetPath() != null) {
            try (InputStream input = Files.newInputStream(settings.datasetPath())) {
                return objectMapper.readValue(input, new TypeReference<List<EvalCase>>() { });
            }
        }
        try (InputStream input = getClass().getClassLoader()
                .getResourceAsStream("rag-eval-dataset.json")) {
            assertThat(input).as("rag-eval-dataset.json must exist on the test classpath").isNotNull();
            return objectMapper.readValue(input, new TypeReference<List<EvalCase>>() { });
        }
    }

    private Map<String, Object> buildReport(
            Settings settings,
            List<EvalCase> cases,
            Map<String, Object> corpus,
            QualitySummary vectorQuality,
            QualitySummary hybridQuality,
            LatencySummary vectorLatency,
            LatencySummary hybridLatency,
            List<PipelineResult> vectorResults,
            List<PipelineResult> hybridResults) {
        Map<String, Object> report = new LinkedHashMap<>();
        report.put("generatedAt", Instant.now().toString());
        report.put("experiment", "vector-only-vs-hybrid-rrf-rerank");

        Map<String, Object> config = new LinkedHashMap<>();
        config.put("dataset", settings.datasetPath() == null
                ? "classpath:rag-eval-dataset.json"
                : settings.datasetPath().toAbsolutePath().toString());
        config.put("datasetCases", cases.size());
        config.put("topK", TOP_K);
        config.put("candidateKPerRoute", CANDIDATE_K);
        config.put("warmupCasesPerMode", settings.warmupCases());
        config.put("repeats", settings.repeats());
        config.put("latencySamplesPerMode", cases.size() * settings.repeats());
        config.put("queryRewriteEnabled", false);
        config.put("answerGenerationEnabled", false);
        config.put("rerankerModelDir", settings.rerankerModelDir());
        config.put("gitCommit", settings.gitCommit());
        report.put("config", config);
        report.put("corpus", corpus);

        Map<String, Object> modes = new LinkedHashMap<>();
        modes.put("vectorOnly", modeMap(vectorQuality, vectorLatency));
        modes.put("hybridRrfRerank", modeMap(hybridQuality, hybridLatency));
        report.put("modes", modes);

        Map<String, Object> deltas = new LinkedHashMap<>();
        deltas.put("hitRatePercentagePoints",
                (hybridQuality.hitRate() - vectorQuality.hitRate()) * 100.0);
        deltas.put("mrr", hybridQuality.mrr() - vectorQuality.mrr());
        deltas.put("ndcg", hybridQuality.ndcg() - vectorQuality.ndcg());
        deltas.put("keywordRecall", hybridQuality.keywordRecall() - vectorQuality.keywordRecall());
        deltas.put("contentCoverage", hybridQuality.contentCoverage() - vectorQuality.contentCoverage());
        deltas.put("p95RetrievalMs", hybridLatency.p95Ms() - vectorLatency.p95Ms());
        report.put("deltas", deltas);

        List<Map<String, Object>> perCase = new ArrayList<>();
        for (int index = 0; index < cases.size(); index++) {
            PipelineResult vector = vectorResults.get(index);
            PipelineResult hybrid = hybridResults.get(index);
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("caseId", cases.get(index).getId());
            row.put("query", cases.get(index).getQuery());
            row.put("vectorOnly", perCaseMode(vector));
            row.put("hybridRrfRerank", perCaseMode(hybrid));
            perCase.add(row);
        }
        report.put("perCase", perCase);
        return report;
    }

    private Map<String, Object> modeMap(QualitySummary quality, LatencySummary latency) {
        Map<String, Object> mode = new LinkedHashMap<>();
        mode.put("quality", quality.toMap());
        mode.put("latencyMs", latency.toMap());
        return mode;
    }

    private Map<String, Object> perCaseMode(PipelineResult result) {
        Map<String, Object> mode = new LinkedHashMap<>();
        mode.put("hit", result.metrics().hit());
        mode.put("reciprocalRank", result.metrics().reciprocalRank());
        mode.put("ndcg", result.metrics().ndcg());
        mode.put("keywordRecall", result.metrics().keywordRecall());
        mode.put("contentCoverage", result.metrics().contentCoverage());
        mode.put("retrievedChunkIds", result.chunkIds());
        mode.put("retrievedDocumentNames", result.documentNames());
        return mode;
    }

    private void writeOutputs(Path outputDir,
                              Map<String, Object> report,
                              List<PipelineResult> vectorResults,
                              List<PipelineResult> hybridResults) throws Exception {
        Files.createDirectories(outputDir);
        Path json = outputDir.resolve("rag-comparison-results.json");
        Path csv = outputDir.resolve("rag-comparison-per-case.csv");
        objectMapper.writerWithDefaultPrettyPrinter().writeValue(json.toFile(), report);

        StringBuilder content = new StringBuilder();
        content.append("case_id,query,vector_hit,hybrid_hit,vector_rr,hybrid_rr,")
                .append("vector_ndcg,hybrid_ndcg,vector_keyword_recall,hybrid_keyword_recall,")
                .append("vector_content_coverage,hybrid_content_coverage\n");
        for (int index = 0; index < vectorResults.size(); index++) {
            PipelineResult vector = vectorResults.get(index);
            PipelineResult hybrid = hybridResults.get(index);
            content.append(vector.evalCase().getId()).append(',')
                    .append(csv(vector.evalCase().getQuery())).append(',')
                    .append(vector.metrics().hit()).append(',')
                    .append(hybrid.metrics().hit()).append(',')
                    .append(vector.metrics().reciprocalRank()).append(',')
                    .append(hybrid.metrics().reciprocalRank()).append(',')
                    .append(vector.metrics().ndcg()).append(',')
                    .append(hybrid.metrics().ndcg()).append(',')
                    .append(vector.metrics().keywordRecall()).append(',')
                    .append(hybrid.metrics().keywordRecall()).append(',')
                    .append(vector.metrics().contentCoverage()).append(',')
                    .append(hybrid.metrics().contentCoverage()).append('\n');
        }
        Files.writeString(csv, content.toString(), StandardCharsets.UTF_8);
        System.out.println("JSON report: " + json.toAbsolutePath());
        System.out.println("CSV report: " + csv.toAbsolutePath());
    }

    private void printSummary(QualitySummary vectorQuality,
                              QualitySummary hybridQuality,
                              LatencySummary vectorLatency,
                              LatencySummary hybridLatency,
                              Settings settings) {
        System.out.println("=== RAG COMPARISON SUMMARY ===");
        System.out.printf(Locale.ROOT,
                "VECTOR_ONLY hit=%d/%d (%.2f%%), MRR=%.4f, nDCG=%.4f, P95=%dms%n",
                vectorQuality.hitCount(), vectorQuality.cases(), vectorQuality.hitRate() * 100,
                vectorQuality.mrr(), vectorQuality.ndcg(), vectorLatency.p95Ms());
        System.out.printf(Locale.ROOT,
                "HYBRID_RRF_RERANK hit=%d/%d (%.2f%%), MRR=%.4f, nDCG=%.4f, P95=%dms%n",
                hybridQuality.hitCount(), hybridQuality.cases(), hybridQuality.hitRate() * 100,
                hybridQuality.mrr(), hybridQuality.ndcg(), hybridLatency.p95Ms());
        System.out.printf(Locale.ROOT,
                "DELTA hit=%.2fpp, MRR=%+.4f, nDCG=%+.4f, P95=%+dms, samples/mode=%d%n",
                (hybridQuality.hitRate() - vectorQuality.hitRate()) * 100,
                hybridQuality.mrr() - vectorQuality.mrr(),
                hybridQuality.ndcg() - vectorQuality.ndcg(),
                hybridLatency.p95Ms() - vectorLatency.p95Ms(),
                vectorLatency.samples());
    }

    private static long elapsedMs(long startedNanos) {
        return Math.max(0, Math.round((System.nanoTime() - startedNanos) / 1_000_000.0));
    }

    private static String csv(String value) {
        String safe = value == null ? "" : value.replace("\"", "\"\"");
        return "\"" + safe + "\"";
    }

    private record PipelineResult(
            EvalCase evalCase,
            RetrievalMetricsCalculator.Metrics metrics,
            long elapsedMs,
            List<String> chunkIds,
            List<String> documentNames) {
    }

    private record QualitySummary(
            int cases,
            int hitCount,
            double hitRate,
            double mrr,
            double ndcg,
            double keywordRecall,
            double contentCoverage) {

        static QualitySummary from(List<PipelineResult> results) {
            int cases = results.size();
            int hits = (int) results.stream().filter(result -> result.metrics().hit()).count();
            return new QualitySummary(
                    cases,
                    hits,
                    hits / (double) cases,
                    average(results, result -> result.metrics().reciprocalRank()),
                    average(results, result -> result.metrics().ndcg()),
                    average(results, result -> result.metrics().keywordRecall()),
                    average(results, result -> result.metrics().contentCoverage()));
        }

        Map<String, Object> toMap() {
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("cases", cases);
            map.put("hitCount", hitCount);
            map.put("hitRate", hitRate);
            map.put("mrr", mrr);
            map.put("ndcg", ndcg);
            map.put("keywordRecall", keywordRecall);
            map.put("contentCoverage", contentCoverage);
            return map;
        }

        private static double average(List<PipelineResult> results,
                                      java.util.function.ToDoubleFunction<PipelineResult> extractor) {
            return results.stream().mapToDouble(extractor).filter(value -> value >= 0)
                    .average().orElse(-1);
        }
    }

    private record LatencySummary(
            int samples,
            long minMs,
            double averageMs,
            long p50Ms,
            long p95Ms,
            long p99Ms,
            long maxMs) {

        static LatencySummary from(List<Long> values) {
            long[] sorted = values.stream().mapToLong(Long::longValue).sorted().toArray();
            return new LatencySummary(
                    sorted.length,
                    sorted[0],
                    values.stream().mapToLong(Long::longValue).average().orElse(0),
                    percentile(sorted, 0.50),
                    percentile(sorted, 0.95),
                    percentile(sorted, 0.99),
                    sorted[sorted.length - 1]);
        }

        Map<String, Object> toMap() {
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("samples", samples);
            map.put("min", minMs);
            map.put("average", averageMs);
            map.put("p50", p50Ms);
            map.put("p95", p95Ms);
            map.put("p99", p99Ms);
            map.put("max", maxMs);
            return map;
        }

        private static long percentile(long[] sorted, double percentile) {
            int index = Math.max(0, (int) Math.ceil(sorted.length * percentile) - 1);
            return sorted[index];
        }
    }

    private record Settings(
            String dbUrl,
            String dbUser,
            String dbPassword,
            String rerankerModelDir,
            Path outputDir,
            Path datasetPath,
            int repeats,
            int warmupCases,
            String gitCommit) {

        static Settings load() {
            String password = firstNonBlank(
                    System.getProperty("rag.eval.db-password"),
                    System.getenv("RAG_EVAL_DB_PASSWORD"));
            if (password == null) {
                throw new IllegalStateException(
                        "Set RAG_EVAL_DB_PASSWORD or -Drag.eval.db-password before running");
            }
            return new Settings(
                    System.getProperty("rag.eval.db-url",
                            "jdbc:postgresql://127.0.0.1:5432/postgres"),
                    System.getProperty("rag.eval.db-user", "postgres"),
                    password,
                    System.getProperty("rag.eval.reranker-dir",
                            "E:\\bge-reranker-base\\dir"),
                    Path.of(System.getProperty("rag.eval.output-dir", "target")),
                    optionalPath(System.getProperty("rag.eval.dataset")),
                    Integer.getInteger("rag.eval.repeats", DEFAULT_REPEATS),
                    Integer.getInteger("rag.eval.warmup-cases", DEFAULT_WARMUP_CASES),
                    System.getProperty("rag.eval.git-commit", "unknown"));
        }

        private static String firstNonBlank(String first, String second) {
            if (first != null && !first.isBlank()) return first;
            if (second != null && !second.isBlank()) return second;
            return null;
        }

        private static Path optionalPath(String value) {
            return value == null || value.isBlank() ? null : Path.of(value);
        }
    }
}
