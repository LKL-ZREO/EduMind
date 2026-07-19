package com.firedemo.demo.Controller;

import com.firedemo.demo.Service.*;
import com.firedemo.demo.common.annotation.RateLimit;
import com.firedemo.demo.common.annotation.RateLimit.Dimension;
import com.firedemo.demo.common.annotation.RateLimit.TimeUnit;
import com.firedemo.demo.Entity.ChatHistory;
import com.firedemo.demo.Entity.ClassInfo;
import com.firedemo.demo.Entity.User;
import com.firedemo.demo.mapper.ClassInfoMapper;
import com.firedemo.demo.utils.JwtUtil;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
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
    private final UserService userService;
    private final ClassInfoMapper classInfoMapper;
    private final JwtUtil jwtUtil;

    private static final String TOKEN_PREFIX = "Bearer ";

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
    public ResponseEntity<List<ChatHistory>> getHistory(HttpServletRequest httpRequest) {
        Long userId = getCurrentUserId(httpRequest);
        if (userId == null) return ResponseEntity.status(401).build();
        return ResponseEntity.ok(chatHistoryService.getUserHistory(userId));
    }

    @PostMapping("/clear")
    public ResponseEntity<Map<String, String>> clearHistory(HttpServletRequest httpRequest) {
        Long userId = getCurrentUserId(httpRequest);
        if (userId == null) return ResponseEntity.status(401).build();
        chatHistoryService.deleteByUserId(userId);
        String newSessionId = UUID.randomUUID().toString();
        log.info("用户 {} 清空了对话历史，新 sessionId: {}", userId, newSessionId);
        return ResponseEntity.ok(Map.of(
                "message", "对话历史已清空",
                "sessionId", newSessionId
        ));
    }

    @RateLimit(dimensions = {Dimension.GLOBAL, Dimension.IP}, count = 10, interval = 60, timeUnit = TimeUnit.SECONDS)
    @GetMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public ResponseEntity<StreamingResponseBody> streamMessage(@RequestParam String message,
                                                               @RequestParam(required = false) String sessionId,
                                                               HttpServletRequest httpRequest,
                                                               HttpServletResponse httpResponse) {
        log.debug("收到流式消息: {}, sessionId: {}", message, sessionId);
        httpResponse.setHeader("Connection", "close");

        Long userId = getCurrentUserId(httpRequest);

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
                        .doOnError(error -> log.error("流式调用失败", error))
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
                } catch (IOException ex) {
                    log.debug("SSE 错误消息写入失败（客户端可能已断开）: {}", ex.getMessage());
                }
            }
        };

        return ResponseEntity.ok()
                .header("X-Accel-Buffering", "no")
                .body(responseBody);
    }

    // ============ 私有方法 ============

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

    private Long getCurrentUserId(HttpServletRequest request) {
        String authHeader = request.getHeader("Authorization");
        if (authHeader == null || !authHeader.startsWith(TOKEN_PREFIX)) return null;

        String token = authHeader.substring(TOKEN_PREFIX.length());
        try {
            return jwtUtil.getUserIdFromToken(token);
        } catch (Exception e) {
            log.warn("解析 token 失败", e);
            return null;
        }
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
}
