package com.firedemo.edumind.integration.onebot;

import com.firedemo.edumind.knowledge.retrieval.RagResult;
import com.firedemo.edumind.knowledge.retrieval.RagSearchRequest;
import com.firedemo.edumind.knowledge.retrieval.RagService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Map;

/**
 * OneBot RAG 接口 - 供 Agent Gateway 调用
 * <p>
 * 委托 {@link RagService} 统一检索，返回增强后的消息。
 */
@Slf4j
@RestController
@RequestMapping("/api/onebot")
@RequiredArgsConstructor
public class OnebotRagController {

    private final RagService ragService;
    private final OneBotStudentContextService studentContextService;

    @Value("${mcp.api-key:}")
    private String mcpApiKey;

    /**
     * RAG 增强接口 - 委托 RagService 统一检索
     */
    @PostMapping("/rag")
    public ResponseEntity<RagResponse> rag(@RequestBody RagRequest request,
                                           HttpServletRequest httpRequest) {
        // 服务间认证：与 MCP 共用同一把 API Key，防止外部直接调用
        if (!apiKeyMatches(httpRequest.getHeader("X-MCP-API-Key"))) {
            log.warn("OneBot RAG 认证失败: 请求来源缺少有效 API Key");
            return ResponseEntity.status(401).build();
        }

        log.debug("RAG request from QQ: {}, message: {}", request.getQq(), request.getMessage());

        try {
            // 1. QQ → 学号 → 班级 → 老师 → 知识库权限
            OneBotStudentContextService.StudentContext context =
                    studentContextService.resolve(request.getQq());

            // 2. 委托 RagService
            RagSearchRequest searchRequest = RagSearchRequest.builder()
                    .query(request.getMessage())
                    .topK(3)
                    .userId(context.userId())
                    .accessibleKbIds(context.accessibleKbIds())
                    .courseId(context.courseId())
                    .enableReranker(true)
                    .format(RagSearchRequest.Format.ENHANCED_MESSAGE)
                    .build();

            RagResult result = ragService.search(searchRequest);

            // 记录"不懂"标记
            studentContextService.recordConfusionIfNeeded(
                    request.getQq(), request.getMessage(), context.studentId());

            return ResponseEntity.ok(new RagResponse(
                    result.getEnhancedMessage(), result.isHasContext()));

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
            // 通过 RagService 间接验证 embedding 可用
            RagResult result = ragService.search(RagSearchRequest.builder()
                    .query("test")
                    .topK(1)
                    .enableReranker(false)
                    .build());
            embeddingOk = true;
        } catch (Exception e) {
            log.debug("RAG 健康检查 embedding 失败: {}", e.getMessage());
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

    /** 常量时间比较，防时序攻击（与 McpApiKeyFilter 一致） */
    private boolean apiKeyMatches(String actual) {
        if (mcpApiKey == null || mcpApiKey.isBlank() || actual == null || actual.isBlank()) {
            return false;
        }
        return MessageDigest.isEqual(
                mcpApiKey.getBytes(StandardCharsets.UTF_8),
                actual.getBytes(StandardCharsets.UTF_8)
        );
    }
}
