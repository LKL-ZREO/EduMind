package com.firedemo.demo.rag;

import com.firedemo.demo.Entity.Course;
import com.firedemo.demo.Entity.DocumentChunk;
import com.firedemo.demo.Service.CourseService;
import io.micrometer.core.annotation.Timed;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

import static com.firedemo.demo.rag.RagMetrics.CandidateSource;
import static com.firedemo.demo.rag.RagMetrics.RewriteOutcome;
import static com.firedemo.demo.rag.RagMetrics.RewriteReason;
import static com.firedemo.demo.rag.RagMetrics.SearchOutcome;
import static com.firedemo.demo.rag.RagMetrics.Stage;

/**
 * RAG 统一检索入口 —— 所有检索路径的唯一实现
 *
 * <pre>
 *   管线：
 *     ① Embedding（原始 query）
 *     ② 向量检索 (pgvector <=>)
 *     ③ 关键词检索（明确 query → 原文；模糊 query → LLM改写后搜）
 *     ④ RRF 融合
 *     ⑤ Reranker 精排（可选）
 *     ⑥ 低置信度兜底：top-1 < 0.3 → LLM改写 → 追加检索
 *     ⑦ 格式化返回
 * </pre>
 *
 * <p>三个调用方：KnowledgeSearchTool / OnebotRagController / DocumentServiceImpl</p>
 */
@Slf4j
@Service
public class RagService {

    private final EmbeddingService embeddingService;
    private final VectorStoreService vectorStoreService;
    private final RrfFusionService rrfFusionService;
    private final RerankerService rerankerService;
    private final QueryRewriter queryRewriter;
    private final CourseService courseService;
    private final RagMetrics ragMetrics;

    /** 每路检索的候选倍数（多取一些参与 RRF 融合） */
    private static final int CANDIDATE_MULTIPLIER = 3;

    /** Reranker 低置信度阈值：top-1 分数低于此值触发兜底改写 */
    private static final double LOW_CONFIDENCE_THRESHOLD = 0.3;

    public RagService(EmbeddingService embeddingService,
                      VectorStoreService vectorStoreService,
                      RrfFusionService rrfFusionService,
                      RerankerService rerankerService,
                      QueryRewriter queryRewriter,
                      CourseService courseService,
                      RagMetrics ragMetrics) {
        this.embeddingService = embeddingService;
        this.vectorStoreService = vectorStoreService;
        this.rrfFusionService = rrfFusionService;
        this.rerankerService = rerankerService;
        this.queryRewriter = queryRewriter;
        this.courseService = courseService;
        this.ragMetrics = ragMetrics;
    }

    /**
     * 统一检索入口。
     * 耗时作为 {@code rag.search} Timer 暴露到 Prometheus。
     */
    @Timed(value = "rag.search", description = "RAG retrieval latency", histogram = true)
    public RagResult search(RagSearchRequest request) {
        long start = System.currentTimeMillis();
        String originalQuery = request.getQuery();
        RagTrace trace = new RagTrace(originalQuery);

        try {
            // ====== ① Embedding ======
            float[] queryEmbedding = ragMetrics.recordStage(
                    trace, Stage.EMBEDDING, () -> embeddingService.embedQuery(originalQuery));

            // ====== ② 向量检索（永远用原始 query） ======
            int candidateCount = request.getTopK() * CANDIDATE_MULTIPLIER;
            List<DocumentChunk> vectorResults = ragMetrics.recordStage(
                    trace, Stage.VECTOR, () -> vectorStoreService.similaritySearch(
                            queryEmbedding, candidateCount,
                            request.getUserId(), request.getAccessibleKbIds()));
            trace.set("vectorHits", vectorResults.size());
            ragMetrics.recordCandidates(CandidateSource.VECTOR, vectorResults.size());

            // ====== ③ 关键词检索 ======
            boolean needsRewrite = queryRewriter.needsRewrite(originalQuery);
            String keywordQuery = originalQuery;
            boolean queryRewritten = false;

            if (needsRewrite) {
                keywordQuery = rewriteQuery(originalQuery, trace, RewriteReason.INITIAL);
                if (!Objects.equals(keywordQuery, originalQuery)) {
                    queryRewritten = true;
                    log.debug("Query rewritten: \"{}\" → \"{}\"", originalQuery, keywordQuery);
                }
            }
            String effectiveKeywordQuery = keywordQuery;
            List<VectorStoreService.ScoredChunk> keywordScored = ragMetrics.recordStage(
                    trace, Stage.KEYWORD, () -> vectorStoreService.keywordSearch(
                            effectiveKeywordQuery, candidateCount,
                            request.getUserId(), request.getAccessibleKbIds()));
            List<DocumentChunk> keywordResults = keywordScored.stream()
                    .map(VectorStoreService.ScoredChunk::chunk)
                    .collect(Collectors.toList());
            trace.set("keywordHits", keywordResults.size());
            ragMetrics.recordCandidates(CandidateSource.KEYWORD, keywordResults.size());

            // ====== ④ 合并候选 ======
            if (vectorResults.isEmpty() && keywordResults.isEmpty()) {
                long elapsed = System.currentTimeMillis() - start;
                ragMetrics.recordCandidates(CandidateSource.FINAL, 0);
                ragMetrics.recordSearchOutcome(SearchOutcome.EMPTY);
                log.info("RAG Trace: {}", trace.finish(0));
                return RagResult.empty(originalQuery, trace, elapsed);
            }

            // ====== ⑤ RRF 融合 ======
            List<RrfFusionService.ScoredChunk> fused = ragMetrics.recordStage(
                    trace, Stage.RRF, () -> rrfFusionService.fuse(vectorResults, keywordResults));
            trace.set("rrfFused", fused.size());
            ragMetrics.recordCandidates(CandidateSource.FUSED, fused.size());

            // ====== ⑥ Reranker 精排 ======
            List<RrfFusionService.ScoredChunk> finalResults = fused;
            if (request.isEnableReranker() && rerankerService.isModelReady()) {
                finalResults = ragMetrics.recordStage(
                        trace, Stage.RERANKER,
                        () -> rerankerService.rerank(originalQuery, fused, request.getTopK(), trace));

                // ====== ⑦ 低置信度兜底 ======
                if (!queryRewritten && !finalResults.isEmpty()) {
                    double topScore = finalResults.get(0).score();
                    if (topScore < LOW_CONFIDENCE_THRESHOLD) {
                        log.info("RAG low confidence (top={}), triggering rewrite fallback",
                                String.format("%.2f", topScore));
                        String rewritten = rewriteQuery(
                                originalQuery, trace, RewriteReason.LOW_CONFIDENCE);
                        if (!Objects.equals(rewritten, originalQuery)) {
                            queryRewritten = true;
                            keywordQuery = rewritten;

                            try {
                                // 追加一轮关键词检索
                                List<VectorStoreService.ScoredChunk> extraKeyword = ragMetrics.recordStage(
                                        trace, Stage.KEYWORD, () -> vectorStoreService.keywordSearch(
                                                rewritten, candidateCount,
                                                request.getUserId(), request.getAccessibleKbIds()));
                                ragMetrics.recordCandidates(
                                        CandidateSource.EXTRA_KEYWORD, extraKeyword.size());

                                if (!extraKeyword.isEmpty()) {
                                    List<DocumentChunk> extraResults = extraKeyword.stream()
                                            .map(VectorStoreService.ScoredChunk::chunk)
                                            .collect(Collectors.toList());

                                    List<DocumentChunk> allVector = new ArrayList<>(vectorResults);
                                    List<DocumentChunk> allKeyword = new ArrayList<>(keywordResults);
                                    allKeyword.addAll(extraResults);

                                    List<RrfFusionService.ScoredChunk> refusion = ragMetrics.recordStage(
                                            trace, Stage.RRF,
                                            () -> rrfFusionService.fuse(allVector, allKeyword));
                                    ragMetrics.recordCandidates(CandidateSource.FUSED, refusion.size());

                                    finalResults = ragMetrics.recordStage(
                                            trace, Stage.RERANKER,
                                            () -> rerankerService.rerank(
                                                    originalQuery, refusion, request.getTopK(), trace));
                                }
                            } catch (RuntimeException e) {
                                log.debug("Low-confidence retrieval fallback failed: {}", e.getMessage());
                            }
                        }
                    }
                }
            } else {
                // 无 Reranker 时截断到 topK
                finalResults = fused.subList(0, Math.min(request.getTopK(), fused.size()));
            }

            // ====== ⑧ 格式化返回 ======
            long elapsed = System.currentTimeMillis() - start;
            ragMetrics.recordCandidates(CandidateSource.FINAL, finalResults.size());
            log.info("RAG Trace: {}", trace.finish(0));

            RagResult.RagResultBuilder builder = RagResult.builder()
                    .hasContext(!finalResults.isEmpty())
                    .results(finalResults)
                    .trace(trace)
                    .elapsedMs(elapsed)
                    .queryRewritten(queryRewritten);

            if (queryRewritten) {
                builder.rewrittenQuery(keywordQuery);
            }

            // 按请求格式输出
            switch (request.getFormat()) {
                case ENHANCED_MESSAGE:
                    builder.enhancedMessage(buildEnhancedMessage(originalQuery, finalResults));
                    builder.formattedContent(buildFormattedContent(finalResults,
                            request.getCourseId(), request.getUserId()));
                    break;
                case FORMATTED_CONTENT:
                    builder.formattedContent(buildFormattedContent(finalResults,
                            request.getCourseId(), request.getUserId()));
                    builder.enhancedMessage(buildEnhancedMessage(originalQuery, finalResults));
                    break;
                case RAW_RESULTS:
                    builder.enhancedMessage(buildEnhancedMessage(originalQuery, finalResults));
                    builder.formattedContent(buildFormattedContent(finalResults,
                            request.getCourseId(), request.getUserId()));
                    break;
            }

            RagResult result = builder.build();
            ragMetrics.recordSearchOutcome(
                    finalResults.isEmpty() ? SearchOutcome.EMPTY : SearchOutcome.SUCCESS);
            return result;

        } catch (RuntimeException e) {
            log.error("RAG search failed", e);
            ragMetrics.recordSearchOutcome(SearchOutcome.ERROR);
            long elapsed = System.currentTimeMillis() - start;
            return RagResult.empty(originalQuery, trace, elapsed);
        }
    }

    private String rewriteQuery(String originalQuery,
                                RagTrace trace,
                                RewriteReason reason) {
        try {
            String rewritten = ragMetrics.recordRewrite(
                    trace, () -> queryRewriter.rewrite(originalQuery));
            boolean changed = rewritten != null
                    && !rewritten.isBlank()
                    && !Objects.equals(rewritten, originalQuery);
            ragMetrics.recordRewriteOutcome(
                    reason, changed ? RewriteOutcome.CHANGED : RewriteOutcome.UNCHANGED);
            return changed ? rewritten : originalQuery;
        } catch (RuntimeException e) {
            ragMetrics.recordRewriteOutcome(reason, RewriteOutcome.ERROR);
            log.debug("RAG query rewrite failed: reason={}, error={}", reason, e.getMessage());
            return originalQuery;
        }
    }

    // ==================== 格式化 ====================

    /**
     * QQ Bot 格式：原始消息 + 【相关知识库内容】
     */
    private String buildEnhancedMessage(String originalQuery,
                                         List<RrfFusionService.ScoredChunk> results) {
        if (results.isEmpty()) return originalQuery;

        StringBuilder context = new StringBuilder();
        for (RrfFusionService.ScoredChunk sc : results) {
            String content = sc.chunk().getContent();
            if (content != null && !content.isBlank()) {
                String snippet = content.length() > 500
                        ? content.substring(0, 500) + "…"
                        : content;
                context.append("\n---\n").append(snippet);
            }
        }
        return originalQuery + "\n\n【相关知识库内容】" + context;
    }

    /**
     * MCP 工具格式：带文档名 + 课程上下文
     */
    private String buildFormattedContent(List<RrfFusionService.ScoredChunk> results,
                                          Long courseId, Long userId) {
        if (results.isEmpty()) return "知识库中未找到相关内容。";

        // 课程上下文头部
        String courseHeader = buildCourseHeader(courseId, userId);

        // 检索结果正文
        String body = results.stream()
                .map(sc -> {
                    var chunk = sc.chunk();
                    String docName = chunk.getDocumentName();
                    String content = chunk.getContent();
                    if (content == null || content.isBlank()) return null;
                    String truncated = content.length() > 500
                            ? content.substring(0, 500) + "…"
                            : content;
                    return (docName != null ? "【" + docName + "】\n" : "") + truncated;
                })
                .filter(java.util.Objects::nonNull)
                .collect(Collectors.joining("\n\n---\n\n"));

        return courseHeader + body;
    }

    /**
     * 构建课程上下文头部（与 KnowledgeSearchTool 中原逻辑一致）
     */
    private String buildCourseHeader(Long courseId, Long userId) {
        if (courseId == null) return "";
        try {
            Course course = courseService.getById(courseId);
            if (course != null) {
                StringBuilder header = new StringBuilder();
                header.append(String.format("\n【系统提示】当前对话所属课程：%s。", course.getName()));
                if (course.getKnowledgeScope() != null && !course.getKnowledgeScope().isEmpty()) {
                    header.append(String.format("知识范围：%s。", course.getKnowledgeScope()));
                }
                header.append("请以此课程助教身份回答问题。\n\n");
                return header.toString();
            }
        } catch (Exception e) {
            log.debug("构建课程头部失败: courseId={}", courseId, e);
        }
        return "";
    }
}
