package com.firedemo.demo.mcp.tools;

import com.firedemo.demo.Entity.DocumentChunk;
import com.firedemo.demo.mcp.ToolDefinition;
import com.firedemo.demo.rag.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * MCP 工具：知识库 RAG 检索（全管线版）
 * <pre>
 *   检索链路：Embedding → pgvector + ILIKE → RRF 融合 → Reranker 精排
 * </pre>
 * LLM 自主判断是否调用此工具，实现 Agentic RAG。
 */
@Slf4j
@Component
public class KnowledgeSearchTool implements ToolDefinition {

    private final EmbeddingService embeddingService;
    private final VectorStoreService vectorStoreService;
    private final RrfFusionService rrfFusionService;
    private final RerankerService rerankerService;
    private final QueryRewriter queryRewriter;

    public KnowledgeSearchTool(EmbeddingService embeddingService,
                               VectorStoreService vectorStoreService,
                               RrfFusionService rrfFusionService,
                               RerankerService rerankerService,
                               QueryRewriter queryRewriter) {
        this.embeddingService = embeddingService;
        this.vectorStoreService = vectorStoreService;
        this.rrfFusionService = rrfFusionService;
        this.rerankerService = rerankerService;
        this.queryRewriter = queryRewriter;
    }

    @Override
    public String name() {
        return "searchKnowledge";
    }

    @Override
    public String description() {
        return "从教学知识库中搜索相关内容，用于回答学生问题或获取参考资料";
    }

    @Override
    public Map<String, Object> inputSchema() {
        return Map.of(
                "type", "object",
                "properties", Map.of(
                        "query", Map.of("type", "string", "description", "搜索关键词或问题，越具体越好"),
                        "topK", Map.of("type", "integer", "description", "返回结果数量，默认3，最大10")
                ),
                "required", List.of("query")
        );
    }

    @Override
    public String execute(Map<String, Object> arguments) {
        String query = (String) arguments.get("query");
        int topK = Math.max(1, Math.min(10, getInt(arguments, "topK", 3)));

        log.info("MCP Tool searchKnowledge: query={}, topK={}", query, topK);

        try {
            RagTrace trace = new RagTrace(query);

            // ① Query Rewrite
            trace.step("rewrite");
            query = queryRewriter.rewrite(query);
            trace.endStep(query);

            // ② Embedding
            trace.step("embed");
            float[] queryEmbedding = embeddingService.embedQuery(query);
            trace.endStep();

            // ③ 双路并行检索
            trace.step("vector");
            List<DocumentChunk> vectorResults = vectorStoreService.similaritySearch(
                    queryEmbedding, topK * 2);
            trace.set("vectorHits", vectorResults.size()).endStep();

            trace.step("keyword");
            List<VectorStoreService.ScoredChunk> keywordScored = vectorStoreService.keywordSearch(
                    query, topK * 2);
            List<DocumentChunk> keywordResults = keywordScored.stream()
                    .map(VectorStoreService.ScoredChunk::chunk)
                    .collect(Collectors.toList());
            trace.set("keywordHits", keywordResults.size()).endStep();

            if (vectorResults.isEmpty() && keywordResults.isEmpty()) {
                log.info("RAG Trace: {}", trace.finish(0));
                return "知识库中未找到与「" + query + "」相关的内容。";
            }

            // ④ RRF 融合
            trace.step("rrf");
            List<RrfFusionService.ScoredChunk> fused = rrfFusionService.fuse(vectorResults, keywordResults);
            trace.set("rrfFused", fused.size()).endStep();

            // ⑤ Reranker 精排
            trace.step("reranker");
            List<RrfFusionService.ScoredChunk> reranked = rerankerService.rerank(query, fused, topK, trace);
            trace.endStep();

            log.info("RAG Trace: {}", trace.finish(0));

            // ⑥ 格式化输出
            return reranked.stream()
                    .map(sc -> {
                        DocumentChunk chunk = sc.chunk();
                        String docName = chunk.getDocumentName();
                        String content = chunk.getContent();
                        String truncated = content.length() > 500
                                ? content.substring(0, 500) + "…"
                                : content;
                        return (docName != null ? "【" + docName + "】\n" : "") + truncated;
                    })
                    .collect(Collectors.joining("\n\n---\n\n"));

        } catch (Exception e) {
            log.error("知识库搜索失败", e);
            return "搜索知识库时出错: " + e.getMessage();
        }
    }

    private int getInt(Map<String, Object> args, String key, int defaultValue) {
        Object val = args.get(key);
        if (val instanceof Number n) return n.intValue();
        if (val instanceof String s) {
            try { return Integer.parseInt(s); } catch (NumberFormatException ignored) {}
        }
        return defaultValue;
    }
}
