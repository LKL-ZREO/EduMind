package com.firedemo.demo.Controller;

import com.firedemo.demo.Service.*;
import com.firedemo.demo.common.annotation.RateLimit;
import com.firedemo.demo.common.annotation.RateLimit.Dimension;
import com.firedemo.demo.common.util.JsonUtil;
import com.firedemo.demo.common.annotation.RateLimit.TimeUnit;
import com.firedemo.demo.DTO.ChatResponse;
import com.firedemo.demo.DTO.EvaluationResultDTO;
import com.firedemo.demo.DTO.GradeRequest;
import com.firedemo.demo.Entity.ChatHistory;
import com.firedemo.demo.Entity.HomeworkEvaluation;
import com.firedemo.demo.Entity.ClassInfo;
import com.firedemo.demo.Entity.User;
import com.firedemo.demo.mapper.ClassInfoMapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;
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
    private final HomeworkResultService homeworkResultService;
    private final UserService userService;
    private final ClassInfoMapper classInfoMapper;
    private final ObjectMapper objectMapper;

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

    @GetMapping("/history")
    public ResponseEntity<List<ChatHistory>> getHistory() {
        Long userId = getCurrentUserId();
        if (userId == null) return ResponseEntity.status(401).build();
        return ResponseEntity.ok(chatHistoryService.getUserHistory(userId));
    }

    @PostMapping("/clear")
    public ResponseEntity<Map<String, String>> clearHistory() {
        Long userId = getCurrentUserId();
        if (userId == null) return ResponseEntity.status(401).build();
        chatHistoryService.deleteByUserId(userId);
        String newSessionId = UUID.randomUUID().toString();
        log.info("用户 {} 清空了对话历史，新 sessionId: {}", userId, newSessionId);
        return ResponseEntity.ok(Map.of("message", "对话历史已清空", "sessionId", newSessionId));
    }

    @RateLimit(dimensions = {Dimension.GLOBAL, Dimension.IP}, count = 10, interval = 60, timeUnit = TimeUnit.SECONDS)
    @GetMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public ResponseEntity<StreamingResponseBody> streamMessage(
            @RequestParam String message,
            @RequestParam(required = false) String sessionId,
            HttpServletResponse httpResponse) {

        log.debug("收到流式消息: {}, sessionId: {}", message, sessionId);
        httpResponse.setHeader("Connection", "close");

        Long userId = getCurrentUserId();
        boolean newSession = (sessionId == null || sessionId.isEmpty());
        if (newSession) sessionId = UUID.randomUUID().toString();

        saveChatHistory(userId, sessionId, "user", message, null);

        List<Map<String, Object>> history = new ArrayList<>();
        if (sessionId != null) {
            List<ChatHistory> recent = chatHistoryService.getHistory(sessionId, 10);
            for (ChatHistory h : recent) {
                history.add(Map.of("role", h.getRole(), "content", h.getContent()));
            }
        }

        Long courseId = resolveCourseId(userId);
        openClawService.registerSessionContext(sessionId, userId, courseId);

        String finalSessionId = sessionId;
        boolean isNewSession = newSession;
        StreamingResponseBody responseBody = outputStream -> {
            StringBuilder responseBuilder = new StringBuilder();
            try {
                if (isNewSession) {
                    String sessionEvent = "data: {\"type\":\"session\",\"sessionId\":\"" + finalSessionId + "\"}\n\n";
                    outputStream.write(sessionEvent.getBytes());
                    outputStream.flush();
                }
                openClawService.streamChat(message, history, finalSessionId)
                        .doOnNext(chunk -> {
                            try {
                                responseBuilder.append(chunk);
                                outputStream.write(("data: " + chunk + "\n\n").getBytes());
                                outputStream.flush();
                            } catch (IOException e) {
                                throw new RuntimeException(e);
                            }
                        })
                        .doOnComplete(() -> {
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
                        .blockLast(java.time.Duration.ofMinutes(5));
            } catch (Exception e) {
                log.error("流式响应异常", e);
                if (userId != null && !responseBuilder.isEmpty()) {
                    saveChatHistory(userId, finalSessionId, "assistant",
                            responseBuilder.toString(), "OpenClaw");
                }
                try {
                    outputStream.write("data: {\"error\":\"服务暂时不可用\"}\n\n".getBytes());
                    outputStream.flush();
                } catch (IOException ex) { /* 客户端可能已断开 */ }
            }
        };

        return ResponseEntity.ok()
                .header("X-Accel-Buffering", "no")
                .body(responseBody);
    }

    @RateLimit(dimensions = {Dimension.GLOBAL, Dimension.USER}, count = 5, interval = 60, timeUnit = TimeUnit.SECONDS)
    @PostMapping("/grade")
    public ResponseEntity<ChatResponse> gradeHomework(@Valid @RequestBody GradeRequest request) {
        Long userId = getCurrentUserId();

        String fileContent = fileStorageService.readFileContent(request.getFilePath());
        String message = String.format("""
            请批改以下作业，并以JSON格式返回结果：

            要求：%s

            文件内容：
            %s

            请使用批改作业助手的标准格式输出结果，必须是合法JSON格式。
            """, request.getRequirement(), fileContent);

        String response = openClawService.chat(message, request.getSessionId());

        String displayContent = response;
        try {
            String jsonStr = JsonUtil.extractJson(response);
            EvaluationResultDTO evaluation = objectMapper.readValue(jsonStr, EvaluationResultDTO.class);
            saveEvaluation(userId, request.getSessionId(), request, evaluation, response);
            displayContent = formatEvaluationToText(evaluation);
        } catch (Exception e) {
            log.warn("解析评价JSON失败，返回原始响应: {}", e.getMessage());
        }

        return ResponseEntity.ok(ChatResponse.builder()
                .content(displayContent).role("assistant")
                .timestamp(Instant.now().toEpochMilli()).model("OpenClaw").done(true).build());
    }

    // ============ 私有方法 ============

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
            entity.setFormatScore(evaluation.getFormatScore() != null ? evaluation.getFormatScore() : 0);
            entity.setOverallComment(evaluation.getOverallComment());
            entity.setStrengths(evaluation.getStrengths() != null ?
                String.join(",", evaluation.getStrengths()) : "");
            entity.setWeaknesses(evaluation.getWeaknesses() != null ?
                String.join(",", evaluation.getWeaknesses()) : "");
            String suggestionsStr = "";
            if (evaluation.getSuggestions() != null) {
                suggestionsStr = evaluation.getSuggestions().stream()
                    .map(s -> s.getPriority() + ": " + s.getIssue() + " -> " + s.getSuggestion())
                    .reduce((a, b) -> a + "\n" + b).orElse("");
            }
            entity.setSuggestions(suggestionsStr);
            entity.setRawResponse(rawResponse);
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

    private String formatEvaluationToText(EvaluationResultDTO evaluation) {
        StringBuilder sb = new StringBuilder();
        sb.append("## 批改结果\n\n");
        sb.append("**总分**: ").append(evaluation.getTotalScore());
        if (evaluation.getMaxScore() != null) sb.append("/").append(evaluation.getMaxScore());
        sb.append("分\n\n");
        sb.append("### 总体评语\n").append(evaluation.getOverallComment()).append("\n\n");
        sb.append("### 亮点\n");
        if (evaluation.getStrengths() != null) {
            for (String h : evaluation.getStrengths()) sb.append("- ").append(h).append("\n");
        }
        sb.append("\n### 改进建议\n");
        if (evaluation.getSuggestions() != null) {
            for (EvaluationResultDTO.SuggestionItem s : evaluation.getSuggestions()) {
                sb.append("**[").append(s.getPriority()).append("]** ")
                  .append(s.getIssue()).append("\n")
                  .append("→ ").append(s.getSuggestion()).append("\n\n");
            }
        }
        return sb.toString();
    }

    private Long resolveCourseId(Long userId) {
        if (userId == null) return null;
        try {
            User user = userService.getById(userId);
            if (user != null && user.getClassId() != null) {
                ClassInfo cls = classInfoMapper.selectById(user.getClassId());
                if (cls != null && cls.getCourseId() != null) return cls.getCourseId();
            }
        } catch (Exception e) {
            log.warn("解析用户课程ID失败: userId={}", userId, e);
        }
        return null;
    }

    private void saveChatHistory(Long userId, String sessionId, String role,
                                  String content, String model) {
        if (userId == null) return;
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
        }
    }

    private Long getCurrentUserId() {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || auth.getDetails() == null) return null;
        if (auth.getDetails() instanceof Long uid) return uid;
        return null;
    }
}
