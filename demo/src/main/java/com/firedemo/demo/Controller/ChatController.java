package com.firedemo.demo.Controller;

import com.firedemo.demo.Service.*;
import com.firedemo.demo.agent.tool.EduAITools;
import com.firedemo.demo.common.annotation.RateLimit;
import com.firedemo.demo.common.annotation.RateLimit.Dimension;
import com.firedemo.demo.common.annotation.RateLimit.TimeUnit;

import com.firedemo.demo.DTO.ChatResponse;
import com.firedemo.demo.DTO.EvaluationResultDTO;
import com.firedemo.demo.DTO.GradeRequest;
import com.firedemo.demo.Entity.ChatHistory;
import com.firedemo.demo.Entity.HomeworkEvaluation;

import com.firedemo.demo.Entity.User;

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
import java.util.ArrayList;
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
    private final ActiveTeacherService activeTeacherService;
    private final HomeworkResultService homeworkResultService;
    private final UserService userService;
    private final JwtUtil jwtUtil;
    private final ObjectMapper objectMapper;
    private final EduAITools eduAITools;

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
        activeTeacherService.touch(userId);

        List<ChatHistory> history = chatHistoryService.getUserHistory(userId);
        return ResponseEntity.ok(history);
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
        activeTeacherService.touch(userId);

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
     * 流式聊天（SSE）— 带 Tool Calling，LLM 自主决定是否检索知识库
     */
    @RateLimit(dimensions = {Dimension.GLOBAL, Dimension.IP}, count = 10, interval = 60, timeUnit = TimeUnit.SECONDS)
    @GetMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public ResponseEntity<StreamingResponseBody> streamMessage(@RequestParam String message,
                                                               @RequestParam(required = false) String sessionId,
                                                               HttpServletRequest httpRequest,
                                                               HttpServletResponse httpResponse) {
        log.debug("收到流式消息: {}, sessionId: {}", message, sessionId);

        // 禁用 keep-alive，防止连接复用导致重复请求
        httpResponse.setHeader("Connection", "close");

        Long userId = getCurrentUserId(httpRequest);
        activeTeacherService.touch(userId);

        // 生成 sessionId（如果没有）
        if (sessionId == null || sessionId.isEmpty()) {
            sessionId = UUID.randomUUID().toString();
        }

        // 保存用户消息（原始消息）
        saveChatHistory(userId, sessionId, "user", message, null);

        // 加载历史上下文（最近 10 条）
        List<Map<String, Object>> history = new ArrayList<>();
        if (sessionId != null) {
            List<ChatHistory> recent = chatHistoryService.getHistory(sessionId, 10);
            for (ChatHistory h : recent) {
                history.add(Map.of("role", h.getRole(), "content", h.getContent()));
            }
        }

        // 流式响应 — 使用 Tool Calling，LLM 自主决定是否检索知识库
        String finalSessionId = sessionId;
        StreamingResponseBody responseBody = outputStream -> {
            StringBuilder responseBuilder = new StringBuilder();

            try {
                openClawService.streamChatWithTools(message, history, eduAITools)
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
                // 即使前端断开，已生成的内容仍在 responseBuilder 中，保存到历史
                if (userId != null && !responseBuilder.isEmpty()) {
                    saveChatHistory(userId, finalSessionId, "assistant",
                            responseBuilder.toString(), "OpenClaw");
                }
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
     * 作业批改 — 全局 + 用户限流
     */
    @RateLimit(dimensions = {Dimension.GLOBAL, Dimension.USER}, count = 5, interval = 60, timeUnit = TimeUnit.SECONDS)
    @PostMapping("/grade")
    public ResponseEntity<ChatResponse> gradeHomework(@Valid @RequestBody GradeRequest request,
                                                       HttpServletRequest httpRequest) {
        log.debug("收到作业批改请求: {}, 文件: {}", request.getRequirement(), request.getFilePath());

        // 获取当前用户ID
        Long userId = getCurrentUserId(httpRequest);
        activeTeacherService.touch(userId);

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
            entity.setContentScore(evaluation.getContentScore() != null ? evaluation.getContentScore() : evaluation.getTotalScore());
            entity.setOverallComment(evaluation.getOverallComment());
            entity.setStrengths(evaluation.getStrengths() != null ? 
                String.join(",", evaluation.getStrengths()) : "");
            entity.setWeaknesses(evaluation.getWeaknesses() != null ?
                String.join(",", evaluation.getWeaknesses()) : "");
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
            User user = userService.getById(userId);
            if (user != null && user.getClassId() != null) {
                entity.setClassId(user.getClassId());
            }
            
            homeworkResultService.saveEvaluation(entity);
            
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
        // 查找第一个 ```json 或 ```
        int start = trimmed.indexOf("```json");
        if (start == -1) {
            start = trimmed.indexOf("```");
        }
        if (start != -1) {
            trimmed = trimmed.substring(start);
            int firstNewline = trimmed.indexOf('\n');
            if (firstNewline != -1) {
                trimmed = trimmed.substring(firstNewline + 1).trim();
            } else {
                trimmed = trimmed.substring(3).trim();
            }
        }
        // 去掉结尾的 ```
        if (trimmed.endsWith("```")) {
            trimmed = trimmed.substring(0, trimmed.length() - 3).trim();
        }
        // 尝试找到 JSON 的完整范围
        int jsonStart = trimmed.indexOf('{');
        int jsonEnd = trimmed.lastIndexOf('}');
        if (jsonStart != -1 && jsonEnd != -1 && jsonEnd > jsonStart) {
            trimmed = trimmed.substring(jsonStart, jsonEnd + 1);
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
        if (evaluation.getStrengths() != null) {
            for (String highlight : evaluation.getStrengths()) {
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
}
