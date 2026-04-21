package com.firedemo.demo.Controller;


import com.firedemo.demo.DTO.ChatRequest;
import com.firedemo.demo.DTO.ChatResponse;
import com.firedemo.demo.DTO.EvaluationResultDTO;
import com.firedemo.demo.DTO.GradeRequest;
import com.firedemo.demo.Entity.ChatHistory;
import com.firedemo.demo.Entity.HomeworkEvaluation;
import com.firedemo.demo.Service.ChatHistoryService;
import com.firedemo.demo.Service.DocumentService;
import com.firedemo.demo.Service.FileStorageService;
import com.firedemo.demo.Service.OpenClawService;

import com.firedemo.demo.Entity.HomeworkKnowledge;
import com.firedemo.demo.Entity.User;
import com.firedemo.demo.mapper.HomeworkEvaluationMapper;
import com.firedemo.demo.mapper.HomeworkKnowledgeMapper;
import com.firedemo.demo.mapper.UserMapper;

import com.firedemo.demo.utils.JwtUtil;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import java.io.IOException;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Slf4j
@RestController
@RequestMapping("/api/chat")
@RequiredArgsConstructor
public class ChatController {

    private final OpenClawService openClawService;
    private final FileStorageService fileStorageService;
    private final ChatHistoryService chatHistoryService;
    private final DocumentService documentService;
    private final HomeworkEvaluationMapper evaluationMapper;
    private final HomeworkKnowledgeMapper knowledgeMapper;
    private final UserMapper userMapper;
    private final JwtUtil jwtUtil;
    private final ObjectMapper objectMapper;

    // RAG 开关：是否启用文档检索
    private static final boolean RAG_ENABLED = true;
    // 检索文档条数
    private static final int RAG_TOP_K = 3;

    private static final String TOKEN_PREFIX = "Bearer ";

    /**
     * 健康检查
     */
    @GetMapping("/health")
    public ResponseEntity<Map<String, Object>> health() {
        boolean openclawHealthy = openClawService.checkConnection();

        Map<String, Object> health = Map.of(
                "status", openclawHealthy ? "UP" : "DEGRADED",
                "timestamp", Instant.now().toString(),
                "openclaw", openclawHealthy ? "UP" : "DOWN"
        );

        return ResponseEntity.status(openclawHealthy ? 200 : 503).body(health);
    }

    /**
     * 获取当前用户的历史记录
     */
    @GetMapping("/history")
    public ResponseEntity<List<ChatHistory>> getHistory(HttpServletRequest httpRequest) {
        Long userId = getCurrentUserId(httpRequest);
        if (userId == null) {
            return ResponseEntity.status(401).build();
        }

        List<ChatHistory> history = chatHistoryService.getUserHistory(userId);
        return ResponseEntity.ok(history);
    }

    /**
     * 获取当前用户的会话列表
     */
    @GetMapping("/sessions")
    public ResponseEntity<List<String>> getSessions(HttpServletRequest httpRequest) {
        Long userId = getCurrentUserId(httpRequest);
        if (userId == null) {
            return ResponseEntity.status(401).build();
        }

        List<String> sessions = chatHistoryService.getUserSessions(userId);
        return ResponseEntity.ok(sessions);
    }

    /**
     * 清空当前用户的所有对话历史，并生成新的 sessionId
     */
    @PostMapping("/clear")
    public ResponseEntity<Map<String, String>> clearHistory(HttpServletRequest httpRequest) {
        Long userId = getCurrentUserId(httpRequest);
        if (userId == null) {
            return ResponseEntity.status(401).build();
        }

        // 删除该用户的所有历史记录
        chatHistoryService.deleteByUserId(userId);

        // 生成新的 sessionId
        String newSessionId = UUID.randomUUID().toString();

        log.info("用户 {} 清空了对话历史，新 sessionId: {}", userId, newSessionId);

        return ResponseEntity.ok(Map.of(
                "message", "对话历史已清空",
                "sessionId", newSessionId
        ));
    }

    /**
     * 普通聊天（非流式）
     */
    @PostMapping("/send")
    public ResponseEntity<ChatResponse> sendMessage(@RequestBody ChatRequest request,
                                                    HttpServletRequest httpRequest) {
        log.debug("收到消息: {}, sessionId: {}", request.getMessage(), request.getSessionId());

        // 获取当前用户ID
        Long userId = getCurrentUserId(httpRequest);

        // 生成 sessionId（如果没有）
        String sessionId = request.getSessionId();
        if (sessionId == null || sessionId.isEmpty()) {
            sessionId = UUID.randomUUID().toString();
        }

        // 保存用户消息
        saveChatHistory(userId, sessionId, "user", request.getMessage(), null);

        // 获取用户 status 并调用 OpenClaw
        Integer status = getCurrentUserStatus(httpRequest);

        // RAG：检索相关文档内容
        String messageWithContext = enhanceMessageWithRag(request.getMessage());

        String response = openClawService.chat(messageWithContext, sessionId, String.valueOf(status));

        // 保存 AI 回复
        saveChatHistory(userId, sessionId, "assistant", response, "OpenClaw");

        return ResponseEntity.ok(ChatResponse.builder()
                .content(response)
                .role("assistant")
                .timestamp(Instant.now().toEpochMilli())
                .model("OpenClaw")
                .sessionId(sessionId)
                .done(true)
                .build());
    }

    /**
     * 流式聊天（SSE）
     */
    @GetMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public ResponseEntity<StreamingResponseBody> streamMessage(@RequestParam String message,
                                                               @RequestParam(required = false) String sessionId,
                                                               HttpServletRequest httpRequest,
                                                               HttpServletResponse httpResponse) {
        log.debug("收到流式消息: {}, sessionId: {}", message, sessionId);

        // 禁用 keep-alive，防止连接复用导致重复请求
        httpResponse.setHeader("Connection", "close");

        Long userId = getCurrentUserId(httpRequest);

        // 生成 sessionId（如果没有）
        if (sessionId == null || sessionId.isEmpty()) {
            sessionId = UUID.randomUUID().toString();
        }

        // RAG增强
        String enhancedMessage = enhanceMessageWithRag(message);

        // 保存用户消息（保存原始消息）
        saveChatHistory(userId, sessionId, "user", message, null);

        // 流式响应
        String finalSessionId = sessionId;
        StreamingResponseBody responseBody = outputStream -> {
            StringBuilder responseBuilder = new StringBuilder();

            try {
                Integer status = getCurrentUserStatus(httpRequest);
                openClawService.streamChat(enhancedMessage, finalSessionId, String.valueOf(status))
                        .doOnNext(chunk -> {
                            log.debug("发送SSE chunk: {}", chunk);
                            try {
                                responseBuilder.append(chunk);
                                String sseLine = "data: " + chunk + "\n\n";
                                outputStream.write(sseLine.getBytes());
                                outputStream.flush();
                            } catch (IOException e) {
                                log.warn("客户端断开连接");
                                throw new RuntimeException(e);
                            }
                        })
                        .doOnError(error -> {
                            log.error("流式调用失败", error);
                        })
                        .doOnComplete(() -> {
                            // 流结束，保存完整回复
                            if (userId != null && !responseBuilder.isEmpty()) {
                                saveChatHistory(userId, finalSessionId, "assistant",
                                        responseBuilder.toString(), "OpenClaw");
                            }
                            try {
                                outputStream.write("data: [DONE]\n\n".getBytes());
                                outputStream.flush();
                            } catch (IOException e) {
                                log.error("写入结束标记失败", e);
                            }
                        })
                        .blockLast(java.time.Duration.ofMinutes(5)); // 5分钟超时
            } catch (Exception e) {
                log.error("流式响应异常", e);
                try {
                    outputStream.write("data: {\"error\":\"服务暂时不可用\"}\n\n".getBytes());
                    outputStream.flush();
                } catch (IOException ignored) {}
            }
        };

        return ResponseEntity.ok()
                .header("X-Accel-Buffering", "no") // 禁用 Nginx 缓冲
                .body(responseBody);
    }

    /**
     * 作业批改（通过文件路径，非流式）
     *
     * @param request 批改请求
     * @return 批改结果
     */
    @PostMapping("/grade")
    public ResponseEntity<ChatResponse> gradeHomework(@Valid @RequestBody GradeRequest request,
                                                       HttpServletRequest httpRequest) {
        log.debug("收到作业批改请求: {}, 文件: {}", request.getRequirement(), request.getFilePath());

        // 获取当前用户ID
        Long userId = getCurrentUserId(httpRequest);

        // 1. 读取文件内容
        String fileContent = fileStorageService.readFileContent(request.getFilePath());

        // 2. 构造批改消息（要求返回JSON格式）
        String message = String.format("""
            请批改以下作业，并以JSON格式返回结果：

            要求：%s

            文件内容：
            %s

            请使用批改作业助手的标准格式输出结果，必须是合法JSON格式。
            """, request.getRequirement(), fileContent);

        // 3. 调用 OpenClaw（传入sessionId保持会话）
        String response = openClawService.chat(message, request.getSessionId());

        // 4. 解析JSON并保存到数据库
        String displayContent = response;
        try {
            // 去掉可能的markdown代码块标记
            String jsonStr = extractJsonFromMarkdown(response);
            EvaluationResultDTO evaluation = objectMapper.readValue(jsonStr, EvaluationResultDTO.class);
            saveEvaluation(userId, request.getSessionId(), request, evaluation, response);
            // 将JSON转换为友好文本格式
            displayContent = formatEvaluationToText(evaluation);
        } catch (Exception e) {
            log.warn("解析评价JSON失败，返回原始响应: {}", e.getMessage());
        }

        return ResponseEntity.ok(ChatResponse.builder()
                .content(displayContent)
                .role("assistant")
                .timestamp(Instant.now().toEpochMilli())
                .model("OpenClaw")
                .done(true)
                .build());
    }

    /**
     * 保存评价结果到数据库
     */
    private void saveEvaluation(Long userId, String sessionId, GradeRequest request,
                                 EvaluationResultDTO evaluation, String rawResponse) {
        try {
            HomeworkEvaluation entity = new HomeworkEvaluation();
            entity.setUserId(userId);
            entity.setSessionId(sessionId);
            entity.setFilePath(request.getFilePath());
            entity.setRequirement(request.getRequirement());
            entity.setTotalScore(evaluation.getTotalScore());
            // 简单处理：总分作为内容分，格式分设为null
            entity.setContentScore(evaluation.getTotalScore());
            entity.setOverallComment(evaluation.getOverallComment());
            entity.setStrengths(evaluation.getHighlights() != null ? 
                String.join(",", evaluation.getHighlights()) : "");
            // 建议列表转为字符串
            String suggestionsStr = "";
            if (evaluation.getSuggestions() != null) {
                suggestionsStr = evaluation.getSuggestions().stream()
                    .map(s -> s.getPriority() + ": " + s.getIssue() + " -> " + s.getSuggestion())
                    .reduce((a, b) -> a + "\n" + b)
                    .orElse("");
            }
            entity.setSuggestions(suggestionsStr);
            entity.setRawResponse(rawResponse);
            
            // 从用户信息获取班级ID
            User user = userMapper.selectById(userId);
            if (user != null && user.getClassId() != null) {
                entity.setClassId(user.getClassId());
            }
            
            evaluationMapper.insert(entity);
            
            // 保存知识点掌握情况
            if (evaluation.getKnowledgePoints() != null) {
                for (EvaluationResultDTO.KnowledgePointItem kp : evaluation.getKnowledgePoints()) {
                    HomeworkKnowledge hk = new HomeworkKnowledge();
                    hk.setEvaluationId(entity.getId());
                    hk.setKnowledgePoint(kp.getName());
                    hk.setMastery(kp.getMastery());
                    hk.setStatus(kp.getStatus());
                    knowledgeMapper.insert(hk);
                }
            }
            
            log.info("评价结果已保存: userId={}, totalScore={}", userId, evaluation.getTotalScore());
        } catch (Exception e) {
            log.error("保存评价结果失败", e);
        }
    }

    /**
     * 从markdown代码块中提取JSON字符串
     */
    private String extractJsonFromMarkdown(String response) {
        if (response == null || response.isEmpty()) {
            return response;
        }
        String trimmed = response.trim();
        // 去掉 ```json 开头
        if (trimmed.startsWith("```json")) {
            trimmed = trimmed.substring(7).trim();
        } else if (trimmed.startsWith("```")) {
            trimmed = trimmed.substring(3).trim();
        }
        // 去掉 ``` 结尾
        if (trimmed.endsWith("```")) {
            trimmed = trimmed.substring(0, trimmed.length() - 3).trim();
        }
        return trimmed;
    }

    /**
     * 将评价DTO格式化为友好文本
     */
    private String formatEvaluationToText(EvaluationResultDTO evaluation) {
        StringBuilder sb = new StringBuilder();
        sb.append("## 批改结果\n\n");
        sb.append("**总分**: ").append(evaluation.getTotalScore());
        if (evaluation.getMaxScore() != null) {
            sb.append("/").append(evaluation.getMaxScore());
        }
        sb.append("分\n\n");
        sb.append("### 总体评语\n");
        sb.append(evaluation.getOverallComment()).append("\n\n");
        sb.append("### 亮点\n");
        if (evaluation.getHighlights() != null) {
            for (String highlight : evaluation.getHighlights()) {
                sb.append("- ").append(highlight).append("\n");
            }
        }
        sb.append("\n### 改进建议\n");
        if (evaluation.getSuggestions() != null) {
            for (EvaluationResultDTO.SuggestionItem s : evaluation.getSuggestions()) {
                sb.append("**[").append(s.getPriority()).append("]** ")
                  .append(s.getIssue()).append("\n");
                sb.append("→ ").append(s.getSuggestion()).append("\n\n");
            }
        }
        return sb.toString();
    }

    /**
     * 作业批改（通过文件路径，流式）
     *
     * @param request 批改请求
     * @return SSE流式响应
     */
    @PostMapping(value = "/grade/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter gradeHomeworkStream(@Valid @RequestBody GradeRequest request) {
        log.debug("收到流式作业批改请求: {}, 文件: {}", request.getRequirement(), request.getFilePath());

        // 1. 读取文件内容
        String fileContent = fileStorageService.readFileContent(request.getFilePath());

        // 2. 构造批改消息
        String message = String.format("""
            请批改以下作业：

            要求：%s

            文件内容：
            %s

            请使用批改作业助手的标准格式输出结果。
            """, request.getRequirement(), fileContent);

        // 3. 流式调用（传入sessionId保持会话）
        return openClawService.streamChatWithSse(message, request.getSessionId());
    }

    // ============ 私有方法 ============

    /**
     * 从请求头获取当前用户ID
     */
    private Long getCurrentUserId(HttpServletRequest request) {
        String authHeader = request.getHeader("Authorization");
        if (authHeader == null || !authHeader.startsWith(TOKEN_PREFIX)) {
            return null;
        }

        String token = authHeader.substring(TOKEN_PREFIX.length());
        try {
            return jwtUtil.getUserIdFromToken(token);
        } catch (Exception e) {
            log.warn("解析 token 失败", e);
            return null;
        }
    }

    /**
     * 从 request attribute 获取当前用户 status
     */
    private Integer getCurrentUserStatus(HttpServletRequest request) {
        return (Integer) request.getAttribute("status");
    }

    /**
     * 保存对话记录
     */
    private void saveChatHistory(Long userId, String sessionId, String role,
                                  String content, String model) {
        if (userId == null) {
            return; // 未登录不保存
        }

        ChatHistory history = new ChatHistory();
        history.setUserId(userId);
        history.setSessionId(sessionId);
        history.setRole(role);
        history.setContent(content);
        history.setModel(model);

        try {
            chatHistoryService.save(history);
        } catch (Exception e) {
            log.warn("保存对话记录失败", e);
            // 不影响主流程
        }
    }

    /**
     * RAG增强：检索相关文档内容并组装到消息中（全库共享）
     */
    private String enhanceMessageWithRag(String message) {
        if (!RAG_ENABLED) {
            return message;
        }

        try {
            // 检索相关文档内容（全库共享）
            List<String> relevantContents = documentService.searchRelevantContent(message, RAG_TOP_K);

            if (relevantContents.isEmpty()) {
                return message;
            }

            // 组装上下文
            String context = String.join("\n\n---\n\n", relevantContents);

            // 构建增强后的消息
            return String.format("""
                基于以下参考文档内容回答问题。如果文档中没有相关信息，请基于你的知识回答。

                参考文档内容：
                %s

                用户问题：%s
                """, context, message);

        } catch (Exception e) {
            log.warn("RAG enhancement failed, using original message", e);
            return message;
        }
    }

    /**
     * 流式响应并保存
     */
    private SseEmitter streamWithSave(Long userId, String sessionId, String message) {
        SseEmitter emitter = new SseEmitter(120000L);
        StringBuilder responseBuilder = new StringBuilder();

        openClawService.streamChat(message, sessionId)
                .subscribe(
                        chunk -> {
                            try {
                                responseBuilder.append(chunk);
                                emitter.send(SseEmitter.event().data(chunk));
                            } catch (Exception e) {
                                log.error("发送 SSE 失败", e);
                                emitter.completeWithError(e);
                            }
                        },
                        error -> {
                            log.error("流式调用失败", error);
                            emitter.completeWithError(error);
                        },
                        () -> {
                            // 流结束，保存完整回复
                            if (userId != null) {
                                saveChatHistory(userId, sessionId, "assistant",
                                        responseBuilder.toString(), "OpenClaw");
                            }
                            emitter.complete();
                        }
                );

        return emitter;
    }
}
