package com.firedemo.demo.Controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.firedemo.demo.Service.*;
import com.firedemo.demo.agent.context.AgentChannel;
import com.firedemo.demo.agent.context.AgentExecutionContext;
import com.firedemo.demo.agent.context.AgentExecutionContextFactory;
import com.firedemo.demo.common.annotation.RateLimit;
import com.firedemo.demo.common.annotation.RateLimit.Dimension;
import com.firedemo.demo.common.annotation.RateLimit.TimeUnit;
import com.firedemo.demo.Entity.ChatHistory;
import com.firedemo.demo.Entity.ClassInfo;
import com.firedemo.demo.Entity.User;
import com.firedemo.demo.mapper.ClassInfoMapper;
import com.firedemo.demo.vision.VisualAsset;
import com.firedemo.demo.vision.VisualAssetService;
import com.firedemo.demo.vision.VisualObservation;
import com.firedemo.demo.vision.VisionTask;
import com.firedemo.demo.vision.VisionUnderstandingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import reactor.core.publisher.Flux;

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
    private final VisualAssetService visualAssetService;
    private final VisionUnderstandingService visionUnderstandingService;
    private final ObjectMapper objectMapper;
    private final AgentExecutionContextFactory executionContextFactory;

    @GetMapping("/health")
    public ResponseEntity<Map<String, Object>> health() {
        boolean llmHealthy = openClawService.checkConnection();
        Map<String, Object> health = Map.of(
                "status", llmHealthy ? "UP" : "DEGRADED",
                "timestamp", Instant.now().toString(),
                "llm", llmHealthy ? "UP" : "DOWN"
        );
        return ResponseEntity.status(llmHealthy ? 200 : 503).body(health);
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
        openClawService.clearMemory(userId);
        chatHistoryService.deleteByUserId(userId);
        String newSessionId = UUID.randomUUID().toString();
        log.info("用户 {} 清空了对话历史，新 sessionId: {}", userId, newSessionId);
        return ResponseEntity.ok(Map.of("message", "对话历史已清空", "sessionId", newSessionId));
    }

    @RateLimit(dimensions = {Dimension.GLOBAL, Dimension.IP}, count = 10, interval = 60, timeUnit = TimeUnit.SECONDS)
    @GetMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public ResponseEntity<StreamingResponseBody> streamMessage(
            @RequestParam String message,
            @RequestParam(required = false) String sessionId) {

        log.debug("收到流式消息: {}, sessionId: {}", message, sessionId);
        Long userId = getCurrentUserId();
        boolean newSession = (sessionId == null || sessionId.isEmpty());
        if (newSession) sessionId = UUID.randomUUID().toString();

        saveChatHistory(userId, sessionId, "user", message, null);

        Long courseId = resolveCourseId(userId);
        AgentExecutionContext context = executionContextFactory.create(
                sessionId, userId, courseId, AgentChannel.WEB);
        openClawService.registerSessionContext(context);

        Flux<String> stream = openClawService.streamChat(message, context, null);
        return sseResponse(stream, sessionId, newSession, userId);
    }

    // ============ 私有方法 ============

    @RateLimit(dimensions = {Dimension.GLOBAL, Dimension.IP}, count = 10, interval = 60, timeUnit = TimeUnit.SECONDS)
    @PostMapping(value = "/multimodal", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<Map<String, Object>> multimodalMessage(
            @RequestParam("message") String message,
            @RequestParam(value = "sessionId", required = false) String sessionId,
            @RequestParam("file") MultipartFile file) throws IOException {

        Long userId = getCurrentUserId();
        boolean newSession = sessionId == null || sessionId.isBlank();
        if (newSession) sessionId = UUID.randomUUID().toString();

        String contentType = file.getContentType();
        if (contentType == null || !contentType.startsWith("image/")) {
            return ResponseEntity.badRequest().body(Map.of("error", "仅支持图片多模态输入"));
        }

        Long courseId = resolveCourseId(userId);
        AgentExecutionContext context = executionContextFactory.create(
                sessionId, userId, courseId, AgentChannel.WEB);
        openClawService.registerSessionContext(context);

        VisualAsset asset = visualAssetService.importBytes(file.getBytes(), contentType);
        VisualObservation observation = visionUnderstandingService.analyze(
                asset.assetId(), VisionTask.DESCRIBE, message);

        String agentMessage = """
                用户上传了一张图片，系统视觉模块已完成分析。
                assetId: %s
                视觉分析结果:
                %s

                用户问题:
                %s
                """.formatted(asset.assetId(), observation.summary(), message);

        String userContent = "[图片] " + (file.getOriginalFilename() != null ? file.getOriginalFilename() : "")
                + "\n" + message;
        saveChatHistory(userId, sessionId, "user", userContent, null);

        String answer = openClawService.chat(agentMessage, context, null);
        saveChatHistory(userId, sessionId, "assistant", answer, "built-in");

        return ResponseEntity.ok(Map.of(
                "content", answer,
                "sessionId", sessionId,
                "newSession", newSession
        ));
    }

    @RateLimit(dimensions = {Dimension.GLOBAL, Dimension.IP}, count = 10, interval = 60, timeUnit = TimeUnit.SECONDS)
    @PostMapping(value = "/multimodal/stream", consumes = MediaType.MULTIPART_FORM_DATA_VALUE,
            produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public ResponseEntity<StreamingResponseBody> multimodalStreamMessage(
            @RequestParam("message") String message,
            @RequestParam(value = "sessionId", required = false) String sessionId,
            @RequestParam("file") MultipartFile file) throws IOException {

        Long userId = getCurrentUserId();
        boolean newSession = sessionId == null || sessionId.isBlank();
        if (newSession) sessionId = UUID.randomUUID().toString();

        String contentType = file.getContentType();
        if (contentType == null || !contentType.startsWith("image/")) {
            return ResponseEntity.badRequest().build();
        }

        Long courseId = resolveCourseId(userId);
        AgentExecutionContext context = executionContextFactory.create(
                sessionId, userId, courseId, AgentChannel.WEB);
        openClawService.registerSessionContext(context);

        VisualAsset asset = visualAssetService.importBytes(file.getBytes(), contentType);
        VisualObservation observation = visionUnderstandingService.analyze(
                asset.assetId(), VisionTask.DESCRIBE, message);
        String agentMessage = """
                用户上传了一张图片，系统视觉模块已完成分析。
                assetId: %s
                视觉分析结果:
                %s

                用户问题:
                %s
                """.formatted(asset.assetId(), observation.summary(), message);

        String userContent = "[图片] " + (file.getOriginalFilename() != null ? file.getOriginalFilename() : "")
                + "\n" + message;
        saveChatHistory(userId, sessionId, "user", userContent, null);

        Flux<String> stream = openClawService.streamChat(agentMessage, context, null);
        return sseResponse(stream, sessionId, newSession, userId);
    }

    private ResponseEntity<StreamingResponseBody> sseResponse(Flux<String> stream,
                                                               String sessionId,
                                                               boolean newSession,
                                                               Long userId) {
        StreamingResponseBody body = outputStream -> writeStream(
                outputStream, stream, sessionId, newSession, userId);
        return ResponseEntity.ok()
                .contentType(MediaType.TEXT_EVENT_STREAM)
                .header("Cache-Control", "no-cache, no-transform")
                .header("X-Accel-Buffering", "no")
                .body(body);
    }

    private void writeStream(OutputStream outputStream,
                             Flux<String> stream,
                             String sessionId,
                             boolean newSession,
                             Long userId) {
        StringBuilder response = new StringBuilder();
        try {
            if (newSession) {
                writeSse(outputStream, "session", Map.of("sessionId", sessionId));
            }
            stream.doOnNext(chunk -> {
                        try {
                            response.append(chunk);
                            writeSse(outputStream, "token", Map.of("content", chunk));
                        } catch (IOException e) {
                            throw new RuntimeException(e);
                        }
                    })
                    .blockLast(java.time.Duration.ofMinutes(5));

            if (userId != null && !response.isEmpty()) {
                saveChatHistory(userId, sessionId, "assistant", response.toString(), "built-in");
            }
            writeSse(outputStream, "done", Map.of());
        } catch (Exception e) {
            log.error("流式响应异常: sessionId={}", sessionId, e);
            if (userId != null && !response.isEmpty()) {
                saveChatHistory(userId, sessionId, "assistant", response.toString(), "built-in");
            }
            try {
                writeSse(outputStream, "error", Map.of("message", "服务暂时不可用"));
            } catch (IOException ignored) {
                // The client may already have disconnected.
            }
        }
    }

    private void writeSse(OutputStream outputStream, String event, Object payload) throws IOException {
        String frame = "event: " + event + "\n"
                + "data: " + objectMapper.writeValueAsString(payload) + "\n\n";
        outputStream.write(frame.getBytes(StandardCharsets.UTF_8));
        outputStream.flush();
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
