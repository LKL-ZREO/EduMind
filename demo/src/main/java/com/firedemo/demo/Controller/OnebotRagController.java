package com.firedemo.demo.Controller;

import com.firedemo.demo.Service.ActiveTeacherService;
import com.firedemo.demo.Service.OpenClawService;
import com.firedemo.demo.agent.tool.EduAITools;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * OneBot RAG 接口 - 供 OpenClaw Gateway 调用
 * <p>
 * Gateway 拿到返回值后直接当回复发送给 QQ 用户。
 * 使用 Tool Calling，LLM 自主决定是否检索知识库。
 */
@Slf4j
@RestController
@RequestMapping("/api/onebot")
@RequiredArgsConstructor
public class OnebotRagController {

    private final OpenClawService openClawService;
    private final EduAITools eduAITools;

    /**
     * RAG 增强接口 - 供 Gateway 调用
     * <p>
     * 改为 Tool Calling 方式：LLM 自主判断是否需要检索知识库，
     * 返回最终回答，Gateway 直接发给 QQ 用户。
     */
    @PostMapping("/rag")
    public ResponseEntity<RagResponse> rag(@RequestBody RagRequest request) {
        log.debug("RAG request from QQ: {}, message: {}", request.getQq(), request.getMessage());

        try {
            // 使用 Tool Calling，LLM 自主决定是否检索知识库
            String result = openClawService.chatWithTools(request.getMessage(), eduAITools);

            return ResponseEntity.ok(new RagResponse(result, true));

        } catch (Exception e) {
            log.error("RAG 处理失败", e);
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
