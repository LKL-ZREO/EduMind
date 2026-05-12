package com.firedemo.demo.Controller;

import com.firedemo.demo.Service.DocumentService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * OneBot RAG 接口 - 供插件调用，只返回增强后的prompt
 * 不直接调用OpenClaw，由Agent处理回复
 *
 * @author 海克斯
 */
@Slf4j
@RestController
@RequestMapping("/api/onebot")
@RequiredArgsConstructor
public class OnebotRagController {

    private final DocumentService documentService;

    private static final int RAG_TOP_K = 6;

    /**
     * RAG 增强接口 - 供 OneBot 插件调用
     * 返回增强后的 prompt，不直接调用 LLM
     */
    @PostMapping("/rag")
    public ResponseEntity<RagResponse> rag(@RequestBody RagRequest request) {
        log.debug("RAG request from QQ: {}, message: {}", request.getQq(), request.getMessage());

        try {
            // 1. RAG 检索
            List<String> relevantContents = documentService.searchRelevantContent(
                    request.getMessage(), RAG_TOP_K);

            // 2. 组装增强 prompt
            String enhancedMessage;
            if (relevantContents.isEmpty()) {
                // 无相关知识，原样返回
                enhancedMessage = request.getMessage();
            } else {
                String context = String.join("\n\n---\n\n", relevantContents);
                enhancedMessage = String.format("""
                    基于以下参考文档内容回答问题。如果文档中没有相关信息，请基于你的知识回答。

                    参考文档内容：
                    %s

                    用户问题：%s
                    """, context, request.getMessage());
            }

            return ResponseEntity.ok(new RagResponse(enhancedMessage, !relevantContents.isEmpty()));

        } catch (Exception e) {
            log.error("RAG 处理失败", e);
            // 失败时原样返回
            return ResponseEntity.ok(new RagResponse(request.getMessage(), false));
        }
    }

    /**
     * 健康检查
     */
    @GetMapping("/rag/health")
    public ResponseEntity<Map<String, String>> health() {
        return ResponseEntity.ok(Map.of("status", "UP"));
    }

    // ============ DTO ============

    @lombok.Data
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    public static class RagRequest {
        private String qq;          // QQ号（用于日志追踪）
        private String message;     // 原始消息
    }

    @lombok.Data
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    public static class RagResponse {
        private String enhancedMessage;  // 增强后的消息
        private boolean hasContext;      // 是否有检索到上下文
    }
}
