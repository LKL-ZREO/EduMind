package com.firedemo.demo.Controller;

import com.firedemo.demo.Entity.ClassInfo;
import com.firedemo.demo.Entity.ClassStudent;
import com.firedemo.demo.Entity.DocumentChunk;
import com.firedemo.demo.mapper.ClassInfoMapper;
import com.firedemo.demo.mapper.ClassStudentMapper;
import com.firedemo.demo.mapper.SharedKbMemberMapper;
import com.firedemo.demo.mapper.StudentQqBindingMapper;
import com.firedemo.demo.rag.EmbeddingService;
import com.firedemo.demo.rag.VectorStoreService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * OneBot RAG 接口 - 供 OpenClaw Gateway 调用
 * <p>
 * 使用本地 RAG 管线（无 LLM 调用），将消息增强后返回。
 * 增强逻辑：向量检索知识库 → 拼接上下文到原始消息末尾。
 * <p>
 * 不调 OpenClaw，避免产生多余会话窗口。
 */
@Slf4j
@RestController
@RequestMapping("/api/onebot")
@RequiredArgsConstructor
public class OnebotRagController {

    private final EmbeddingService embeddingService;
    private final VectorStoreService vectorStoreService;
    private final StudentQqBindingMapper studentQqBindingMapper;
    private final ClassStudentMapper classStudentMapper;
    private final ClassInfoMapper classInfoMapper;
    private final SharedKbMemberMapper sharedKbMemberMapper;

    /**
     * RAG 增强接口 - 本地检索，不调 LLM
     * <p>
     * 返回 enhancedMessage = 原始消息 + 知识库上下文，
     * Gateway 拿到后作为增强消息发给 Agent 处理。
     */
    @PostMapping("/rag")
    public ResponseEntity<RagResponse> rag(@RequestBody RagRequest request) {
        log.debug("RAG request from QQ: {}, message: {}", request.getQq(), request.getMessage());

        try {
            // 1. 从QQ号解析用户上下文（QQ → 学号 → 班级 → 老师 → 知识库权限）
            Long userId = null;
            Set<Long> accessibleKbIds = null;
            try {
                String studentId = studentQqBindingMapper.selectStudentIdByQq(request.getQq());
                if (studentId != null) {
                    ClassStudent cs = classStudentMapper.selectByStudentId(studentId);
                    if (cs != null && cs.getClassId() != null) {
                        ClassInfo classInfo = classInfoMapper.selectById(cs.getClassId());
                        if (classInfo != null && classInfo.getTeacherId() != null) {
                            userId = classInfo.getTeacherId();
                            accessibleKbIds = sharedKbMemberMapper.selectKbIdsByUserId(userId);
                        }
                    }
                }
            } catch (Exception e) {
                log.debug("无法从QQ解析用户上下文，回退搜全库: qq={}", request.getQq(), e);
            }

            // 2. 向量检索知识库（优先过滤，无上下文则搜全库）
            float[] embedding = embeddingService.embedQuery(request.getMessage());
            List<DocumentChunk> chunks = vectorStoreService.similaritySearch(
                    embedding, 3, userId, accessibleKbIds);

            // 2. 无命中则返回原消息
            if (chunks == null || chunks.isEmpty()) {
                return ResponseEntity.ok(new RagResponse(request.getMessage(), false));
            }

            // 3. 拼接上下文
            StringBuilder context = new StringBuilder();
            for (DocumentChunk chunk : chunks) {
                if (chunk.getContent() != null && !chunk.getContent().isBlank()) {
                    String snippet = chunk.getContent().length() > 500
                            ? chunk.getContent().substring(0, 500) + "…"
                            : chunk.getContent();
                    context.append("\n---\n").append(snippet);
                }
            }

            String enhanced = request.getMessage()
                    + "\n\n【相关知识库内容】" + context;

            return ResponseEntity.ok(new RagResponse(enhanced, true));

        } catch (Exception e) {
            log.error("RAG 增强失败，降级返回原消息", e);
            return ResponseEntity.ok(new RagResponse(request.getMessage(), false));
        }
    }

    /**
     * 健康检查
     */
    @GetMapping("/rag/health")
    public ResponseEntity<Map<String, String>> health() {
        boolean embeddingOk = false;
        try {
            embeddingService.embedQuery("test");
            embeddingOk = true;
        } catch (Exception ignored) {
        }
        return ResponseEntity.ok(Map.of(
                "status", embeddingOk ? "UP" : "DEGRADED",
                "embedding", String.valueOf(embeddingOk)
        ));
    }

    // ============ DTO ============

    @lombok.Data
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    public static class RagRequest {
        private String qq;
        private String message;
    }

    @lombok.Data
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    public static class RagResponse {
        private String enhancedMessage;
        private boolean hasContext;
    }
}
