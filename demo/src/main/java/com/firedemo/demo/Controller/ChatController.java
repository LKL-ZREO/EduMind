package com.firedemo.demo.Controller;


import com.firedemo.demo.DTO.ChatRequest;
import com.firedemo.demo.DTO.ChatResponse;
import com.firedemo.demo.DTO.GradeRequest;
import com.firedemo.demo.Service.FileStorageService;
import com.firedemo.demo.Service.OpenClawService;
import com.firedemo.demo.entity.ChatHistory;
import com.firedemo.demo.service.ChatHistoryService;

import com.firedemo.demo.utils.JwtUtil;
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
    private final JwtUtil jwtUtil;

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

        // 调用 OpenClaw
        String response = openClawService.chat(request.getMessage(), sessionId);

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

        // 保存用户消息
        saveChatHistory(userId, sessionId, "user", message, null);

        // 流式响应
        String finalSessionId = sessionId;
        StreamingResponseBody responseBody = outputStream -> {
            StringBuilder responseBuilder = new StringBuilder();
            
            try {
                openClawService.streamChat(message, finalSessionId)
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
    public ResponseEntity<ChatResponse> gradeHomework(@Valid @RequestBody GradeRequest request) {
        log.debug("收到作业批改请求: {}, 文件: {}", request.getRequirement(), request.getFilePath());

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

        // 3. 调用 OpenClaw（传入sessionId保持会话）
        String response = openClawService.chat(message, request.getSessionId());

        return ResponseEntity.ok(ChatResponse.builder()
                .content(response)
                .role("assistant")
                .timestamp(Instant.now().toEpochMilli())
                .model("OpenClaw")
                .done(true)
                .build());
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
