package com.firedemo.demo.rag;

import com.firedemo.demo.Entity.DocumentChunk;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * RAG controller — document ingestion and utilities only.
 * Query is handled via MCP tool calling (Agentic RAG).
 */
@Slf4j
@RestController
@RequestMapping("/api/rag")
@RequiredArgsConstructor
public class RAGController {

    private final SmartChunkService chunkService;
    private final EmbeddingService embeddingService;
    private final VectorStoreService vectorStoreService;

    // ==================== document ingestion ====================

    @PostMapping("/chunk")
    public Map<String, Object> chunkDocument(@RequestBody ChunkRequest request) {
        log.info("Chunking document, length: {}", request.getContent().length());

        SmartChunkService.ChunkConfig config = new SmartChunkService.ChunkConfig();
        if (request.getMaxTokens() != null) config.setMaxTokens(request.getMaxTokens());
        if (request.getOverlapTokens() != null) config.setOverlapTokens(request.getOverlapTokens());

        List<DocumentChunk> chunks = chunkService.chunk(request.getContent(), config);

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

    // ==================== embed test ====================

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

    // ==================== upload check ====================

    @GetMapping("/check-uploaded")
    public Map<String, Object> checkUploaded(@RequestParam("classId") Long classId) {
        // 仪表盘 RAG 上传已禁用，始终返回 false
        Map<String, Object> result = new HashMap<>();
        result.put("classId", classId);
        result.put("uploadedToday", false);
        result.put("message", "仪表盘数据不应存入知识库");
        return result;
    }

    // ==================== DTOs ====================

    @lombok.Data
    public static class ChunkRequest {
        private String content;
        private String documentId;
        private String documentName;
        private Integer maxTokens;
        private Integer overlapTokens;
    }
}
