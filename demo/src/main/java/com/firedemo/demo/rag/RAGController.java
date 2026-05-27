package com.firedemo.demo.rag;

import com.firedemo.demo.Entity.DocumentChunk;
import com.firedemo.demo.Service.OpenClawService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import reactor.core.publisher.Flux;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * RAG控制器 - 集成OpenClaw流式响应
 */
@Slf4j
@RestController
@RequestMapping("/api/rag")
@RequiredArgsConstructor
public class RAGController {

    private final SmartChunkService chunkService;
    private final EmbeddingService embeddingService;
    private final OpenClawService openClawService;
    private final VectorStoreService vectorStoreService;

    /**
     * 智能切割文档并保存
     */
    @PostMapping("/chunk")
    public Map<String, Object> chunkDocument(@RequestBody ChunkRequest request) {
        log.info("Chunking document, length: {}", request.getContent().length());
        
        SmartChunkService.ChunkConfig config = new SmartChunkService.ChunkConfig();
        if (request.getMaxTokens() != null) {
            config.setMaxTokens(request.getMaxTokens());
        }
        if (request.getOverlapTokens() != null) {
            config.setOverlapTokens(request.getOverlapTokens());
        }
        
        List<DocumentChunk> chunks = chunkService.chunk(request.getContent(), config);
        
        // 保存到向量存储
        if (request.getDocumentId() != null) {
            chunks.forEach(c -> c.setDocumentName(request.getDocumentName()));
            vectorStoreService.saveChunks(request.getDocumentId(), chunks);
        }
        
        Map<String, Object> result = new HashMap<>();
        result.put("totalChunks", chunks.size());
        result.put("documentId", request.getDocumentId());
        result.put("chunks", chunks);
        
        return result;
    }
    
    /**
     * 从存储中检索并查询
     */
    @PostMapping("/query/stored")
    public String ragQueryFromStore(@RequestBody StoreQueryRequest request) {
        log.info("RAG query from store: {}", request.getQuery());
        
        // 1. 获取查询向量
        float[] queryEmbedding = embeddingService.embed(request.getQuery());
        
        // 2. 相似度搜索（带用户隔离）
        List<DocumentChunk> relevantChunks = vectorStoreService.similaritySearch(queryEmbedding, 
            request.getTopK() != null ? request.getTopK() : 3,
            request.getUserId(), request.getAccessibleKbIds());
        
        if (relevantChunks.isEmpty()) {
            return "未找到相关文档内容";
        }
        
        // 3. 组装上下文
        String context = relevantChunks.stream()
            .map(DocumentChunk::getContent)
            .collect(Collectors.joining("\n\n---\n\n"));
        
        // 4. 构建prompt并查询
        String prompt = buildRagPrompt(request.getQuery(), context);
        return openClawService.chat(prompt, request.getSessionId());
    }

    /**
     * 测试嵌入
     */
    @PostMapping("/embed")
    public Map<String, Object> embed(@RequestBody Map<String, String> request) {
        String text = request.get("text");
        float[] embedding = embeddingService.embed(text);
        
        Map<String, Object> result = new HashMap<>();
        result.put("text", text);
        result.put("embeddingSize", embedding.length);
        result.put("embedding", embedding);
        
        return result;
    }

    /**
     * RAG查询 - 非流式
     * 流程: 切割文档 -> 检索相关chunk -> 组装上下文 -> 调用OpenClaw
     */
    @PostMapping("/query")
    public String ragQuery(@RequestBody RagQueryRequest request) {
        log.info("RAG query: {}", request.getQuery());
        
        // 1. 切割文档
        List<DocumentChunk> chunks = chunkService.chunk(request.getDocument(), request.getChunkConfig());
        
        // 2. 计算查询与每个chunk的相似度
        float[] queryEmbedding = embeddingService.embed(request.getQuery());
        List<ScoredChunk> scoredChunks = chunks.stream()
            .map(chunk -> new ScoredChunk(chunk, cosineSimilarity(queryEmbedding, chunk.getEmbedding())))
            .sorted((a, b) -> Double.compare(b.score, a.score))
            .limit(request.getTopK() != null ? request.getTopK() : 3)
            .collect(Collectors.toList());
        
        // 3. 组装上下文
        String context = scoredChunks.stream()
            .map(sc -> sc.chunk.getContent())
            .collect(Collectors.joining("\n\n---\n\n"));
        
        // 4. 构建prompt
        String prompt = buildRagPrompt(request.getQuery(), context);
        
        // 5. 调用OpenClaw
        return openClawService.chat(prompt, request.getSessionId());
    }

    /**
     * RAG查询 - 流式响应 (SSE)
     */
    @PostMapping("/query/stream")
    public SseEmitter ragQueryStream(@RequestBody RagQueryRequest request) {
        log.info("RAG stream query: {}", request.getQuery());
        
        // 1. 切割文档
        List<DocumentChunk> chunks = chunkService.chunk(request.getDocument(), request.getChunkConfig());
        
        // 2. 检索相关chunk
        float[] queryEmbedding = embeddingService.embed(request.getQuery());
        List<ScoredChunk> scoredChunks = chunks.stream()
            .map(chunk -> new ScoredChunk(chunk, cosineSimilarity(queryEmbedding, chunk.getEmbedding())))
            .sorted((a, b) -> Double.compare(b.score, a.score))
            .limit(request.getTopK() != null ? request.getTopK() : 3)
            .collect(Collectors.toList());
        
        // 3. 组装上下文
        String context = scoredChunks.stream()
            .map(sc -> sc.chunk.getContent())
            .collect(Collectors.joining("\n\n---\n\n"));
        
        // 4. 构建prompt并流式调用
        String prompt = buildRagPrompt(request.getQuery(), context);
        
        return openClawService.streamChatWithSse(prompt, request.getSessionId());
    }

    /**
     * RAG查询 - 流式响应 (Flux)
     */
    @PostMapping("/query/flux")
    public Flux<String> ragQueryFlux(@RequestBody RagQueryRequest request) {
        log.info("RAG flux query: {}", request.getQuery());
        
        List<DocumentChunk> chunks = chunkService.chunk(request.getDocument(), request.getChunkConfig());
        
        float[] queryEmbedding = embeddingService.embed(request.getQuery());
        List<ScoredChunk> scoredChunks = chunks.stream()
            .map(chunk -> new ScoredChunk(chunk, cosineSimilarity(queryEmbedding, chunk.getEmbedding())))
            .sorted((a, b) -> Double.compare(b.score, a.score))
            .limit(request.getTopK() != null ? request.getTopK() : 3)
            .collect(Collectors.toList());
        
        String context = scoredChunks.stream()
            .map(sc -> sc.chunk.getContent())
            .collect(Collectors.joining("\n\n---\n\n"));
        
        String prompt = buildRagPrompt(request.getQuery(), context);
        
        return openClawService.streamChat(prompt, request.getSessionId());
    }

    /**
     * 构建RAG Prompt
     */
    private String buildRagPrompt(String query, String context) {
        return String.format("""
            基于以下上下文回答问题。如果上下文不包含答案，请说明无法回答。

            上下文：
            %s

            问题：%s

            回答：
            """, context, query);
    }

    /**
     * 计算余弦相似度
     */
    private double cosineSimilarity(float[] a, float[] b) {
        if (a.length != b.length) return 0;
        
        double dot = 0;
        double normA = 0;
        double normB = 0;
        
        for (int i = 0; i < a.length; i++) {
            dot += a[i] * b[i];
            normA += a[i] * a[i];
            normB += b[i] * b[i];
        }
        
        return dot / (Math.sqrt(normA) * Math.sqrt(normB));
    }

    /**
     * 带分数的chunk
     */
    private record ScoredChunk(DocumentChunk chunk, double score) {}

    /**
     * 请求DTO
     */
    @lombok.Data
    public static class ChunkRequest {
        private String content;
        private String documentId;
        private String documentName;
        private Integer maxTokens;
        private Integer overlapTokens;
    }

    /**
     * RAG查询请求
     */
    @lombok.Data
    public static class RagQueryRequest {
        private String query;
        private String document;
        private String sessionId;
        private Integer topK;
        private SmartChunkService.ChunkConfig chunkConfig;
    }
    
    /**
     * 从存储查询请求
     */
    @lombok.Data
    public static class StoreQueryRequest {
        private String query;
        private String sessionId;
        private Integer topK;
        /**
         * 当前用户ID，NULL=全库检索（兼容模式）
         */
        private Long userId;
        /**
         * 用户可访问的共享知识库ID集合，NULL/空=不查共享库
         */
        private Set<Long> accessibleKbIds;
    }
    
    /**
     * 检查今天是否已上传过该班级的数据
     */
    @GetMapping("/check-uploaded")
    public Map<String, Object> checkUploaded(@RequestParam("classId") Long classId) {
        String docIdPrefix = "dashboard_" + classId;
        boolean exists = vectorStoreService.existsToday(docIdPrefix);
        
        Map<String, Object> result = new HashMap<>();
        result.put("classId", classId);
        result.put("uploadedToday", exists);
        
        if (exists) {
            result.put("message", "今天已上传过该班级数据，请先删除旧数据再重新上传");
        }
        
        return result;
    }
}
