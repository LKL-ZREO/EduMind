package com.firedemo.edumind.integration.onebot;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.firedemo.edumind.classroom.ClassInfo;
import com.firedemo.edumind.classroom.ClassStudent;
import com.firedemo.edumind.assistant.AgentService;
import com.firedemo.edumind.assistant.context.AgentChannel;
import com.firedemo.edumind.assistant.context.AgentExecutionContext;
import com.firedemo.edumind.assistant.context.AgentExecutionContextFactory;
import com.firedemo.edumind.classroom.ClassInfoMapper;
import com.firedemo.edumind.classroom.ClassStudentMapper;
import com.firedemo.edumind.classroom.StudentQqBindingMapper;
import com.firedemo.edumind.assistant.vision.CqImageMessageParser;
import com.firedemo.edumind.assistant.vision.VisualAsset;
import com.firedemo.edumind.assistant.vision.VisualAssetService;
import com.firedemo.edumind.assistant.vision.VisualObservation;
import com.firedemo.edumind.assistant.vision.VisionTask;
import com.firedemo.edumind.assistant.vision.VisionUnderstandingService;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.WebSocket;
import java.nio.ByteBuffer;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * OneBot v11 WebSocket 双向通信客户端 — 替代 Agent OneBot 插件
 */
@Slf4j
@Component
public class OneBotWebSocketClient {

    private final OneBotProperties properties;
    private final AgentService agentLoopService;
    private final ObjectMapper objectMapper;
    private final StudentQqBindingMapper studentQqBindingMapper;
    private final ClassStudentMapper classStudentMapper;
    private final ClassInfoMapper classInfoMapper;
    private final AgentExecutionContextFactory executionContextFactory;
    private final CqImageMessageParser cqImageMessageParser;
    private final VisualAssetService visualAssetService;
    private final VisionUnderstandingService visionUnderstandingService;

    private volatile WebSocket webSocket;
    private volatile boolean running = false;
    private final AtomicInteger echoCounter = new AtomicInteger(0);

    /** 机器人的 QQ 号（从 NapCat 事件中自动获取） */
    private volatile String selfId = "";

    /** QQ 会话历史缓存，30 分钟过期 */
    private final Cache<String, List<Map<String, String>>> conversationCache = Caffeine.newBuilder()
            .expireAfterAccess(Duration.ofMinutes(30))
            .maximumSize(500)
            .build();

    private static final int MAX_HISTORY = 20;
    /** 超过此长度使用合并转发 */
    private static final int FORWARD_THRESHOLD = 500;
    /** 合并转发每段最大字数 */
    private static final int FORWARD_CHUNK_SIZE = 400;

    public OneBotWebSocketClient(OneBotProperties properties,
                                 AgentService agentLoopService,
                                 ObjectMapper objectMapper,
                                 StudentQqBindingMapper studentQqBindingMapper,
                                 ClassStudentMapper classStudentMapper,
                                 ClassInfoMapper classInfoMapper,
                                 AgentExecutionContextFactory executionContextFactory,
                                 CqImageMessageParser cqImageMessageParser,
                                 VisualAssetService visualAssetService,
                                 VisionUnderstandingService visionUnderstandingService) {
        this.properties = properties;
        this.agentLoopService = agentLoopService;
        this.objectMapper = objectMapper;
        this.studentQqBindingMapper = studentQqBindingMapper;
        this.classStudentMapper = classStudentMapper;
        this.classInfoMapper = classInfoMapper;
        this.executionContextFactory = executionContextFactory;
        this.cqImageMessageParser = cqImageMessageParser;
        this.visualAssetService = visualAssetService;
        this.visionUnderstandingService = visionUnderstandingService;
    }

    // ========================================================================
    //  生命周期
    // ========================================================================

    @PostConstruct
    public void start() {
        if (!properties.getWs().isEnabled()) {
            log.info("OneBot WebSocket 已禁用，跳过连接");
            return;
        }
        running = true;
        Thread connectThread = new Thread(this::connect, "onebot-ws-connect");
        connectThread.setDaemon(true);
        connectThread.start();
    }

    @PreDestroy
    public void stop() {
        running = false;
        if (webSocket != null) {
            try {
                webSocket.sendClose(1000, "服务关闭").get(3, TimeUnit.SECONDS);
            } catch (Exception e) {
                log.debug("WebSocket 关闭发送失败 (服务正在停止): {}", e.getMessage());
            }
        }
        log.info("OneBot WebSocket 已关闭");
    }

    // ========================================================================
    //  公开 API — 发送消息
    // ========================================================================

    public void sendGroupMessage(String groupId, String message) {
        if (groupId == null || groupId.isEmpty() || message == null || message.isEmpty()) return;
        sendAction(buildAction("send_group_msg", Map.of("group_id", groupId, "message", message)),
                "group=" + groupId);
    }

    public void sendPrivateMessage(String qqNumber, String message) {
        if (qqNumber == null || qqNumber.isEmpty() || message == null || message.isEmpty()) return;
        sendAction(buildAction("send_private_msg", Map.of("user_id", qqNumber, "message", message)),
                "qq=" + qqNumber);
    }

    // ========================================================================
    //  智能回复 — 短消息直接发，长消息合并转发
    // ========================================================================

    /** 群回复：短→直接发，长→合并转发 */
    private void sendGroupReply(String groupId, String qqNumber, String reply) {
        if (reply.length() <= FORWARD_THRESHOLD) {
            sendGroupMessage(groupId, "[CQ:at,qq=" + qqNumber + "] " + reply);
        } else {
            sendGroupForward(groupId, qqNumber, reply);
        }
    }

    /** 私聊回复：短→直接发，长→合并转发 */
    private void sendPrivateReply(String qqNumber, String reply) {
        if (reply.length() <= FORWARD_THRESHOLD) {
            sendPrivateMessage(qqNumber, reply);
        } else {
            sendPrivateForward(qqNumber, reply);
        }
    }

    /** 群聊合并转发 */
    private void sendGroupForward(String groupId, String qqNumber, String reply) {
        if (selfId.isEmpty()) {
            // 没拿到机器人的 QQ 号，降级为分片发送
            log.warn("selfId 未知，降级为分片发送");
            for (String chunk : splitReply(reply)) {
                sendGroupMessage(groupId, "[CQ:at,qq=" + qqNumber + "] " + chunk);
            }
            return;
        }

        List<Map<String, Object>> messages = new ArrayList<>();
        for (String chunk : splitReply(reply)) {
            messages.add(Map.of(
                    "type", "node",
                    "data", Map.of(
                            "name", "教学助手",
                            "uin", selfId,
                            "content", chunk
                    )
            ));
        }

        sendAction(buildAction("send_group_forward_msg", Map.of(
                "group_id", groupId,
                "messages", messages
        )), "group-forward=" + groupId);
    }

    /** 私聊合并转发 */
    private void sendPrivateForward(String qqNumber, String reply) {
        if (selfId.isEmpty()) {
            for (String chunk : splitReply(reply)) {
                sendPrivateMessage(qqNumber, chunk);
            }
            return;
        }

        List<Map<String, Object>> messages = new ArrayList<>();
        for (String chunk : splitReply(reply)) {
            messages.add(Map.of(
                    "type", "node",
                    "data", Map.of(
                            "name", "教学助手",
                            "uin", selfId,
                            "content", chunk
                    )
            ));
        }

        sendAction(buildAction("send_private_forward_msg", Map.of(
                "user_id", qqNumber,
                "messages", messages
        )), "private-forward=" + qqNumber);
    }

    /** 按段落智能分片，尽量保持内容完整 */
    private List<String> splitReply(String reply) {
        List<String> chunks = new ArrayList<>();
        StringBuilder buf = new StringBuilder();
        for (String line : reply.split("\n")) {
            if (buf.length() + line.length() > FORWARD_CHUNK_SIZE && buf.length() > 0) {
                chunks.add(buf.toString().trim());
                buf.setLength(0);
            }
            buf.append(line).append("\n");
        }
        if (buf.length() > 0) chunks.add(buf.toString().trim());
        return chunks.isEmpty() ? List.of(reply) : chunks;
    }

    // ========================================================================
    //  WebSocket 发送
    // ========================================================================

    private Map<String, Object> buildAction(String action, Map<String, Object> params) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("action", action);
        map.put("params", params);
        map.put("echo", "edumind-" + echoCounter.incrementAndGet());
        return map;
    }

    private void sendAction(Map<String, Object> action, String logCtx) {
        WebSocket ws = this.webSocket;
        if (ws == null) {
            log.warn("WebSocket 未连接，无法发送消息: {}", logCtx);
            return;
        }
        try {
            String json = objectMapper.writeValueAsString(action);
            ws.sendText(json, true);
            log.debug("OneBot WS: action={}, {}", action.get("action"), logCtx);
        } catch (Exception e) {
            log.error("OneBot WS 发送失败: {}", logCtx, e);
        }
    }

    // ========================================================================
    //  WebSocket 连接
    // ========================================================================

    private void connect() {
        String wsUrl = properties.getWs().getUrl() + properties.getWs().getPath();
        String token = properties.getWs().getAccessToken();

        log.info("正在连接 OneBot WebSocket: {}", wsUrl);

        try {
            HttpClient client = HttpClient.newHttpClient();
            CompletableFuture<WebSocket> future = client.newWebSocketBuilder()
                    .header("Authorization", "Bearer " + token)
                    .connectTimeout(Duration.ofSeconds(10))
                    .buildAsync(URI.create(wsUrl), new OneBotListener());

            webSocket = future.get(10, TimeUnit.SECONDS);
            log.info("OneBot WebSocket 连接成功: {}", wsUrl);
        } catch (Exception e) {
            log.error("OneBot WebSocket 连接失败: {} — 30秒后重试", e.getMessage());
            if (running) {
                try { Thread.sleep(30_000); } catch (InterruptedException ignored) {}
                if (running) connect();
            }
        }
    }

    // ========================================================================
    //  WebSocket 监听器
    // ========================================================================

    private class OneBotListener implements WebSocket.Listener {

        private final StringBuilder buffer = new StringBuilder();

        @Override
        public void onOpen(WebSocket ws) {
            ws.request(1);
            log.info("OneBot WebSocket 监听已开启（收发双向）");
        }

        @Override
        public CompletionStage<?> onText(WebSocket ws, CharSequence data, boolean last) {
            buffer.append(data);
            if (last) {
                String message = buffer.toString();
                buffer.setLength(0);
                try {
                    handleInbound(message);
                } catch (Exception e) {
                    log.error("处理 OneBot 事件异常", e);
                }
            }
            ws.request(1);
            return null;
        }

        @Override
        public CompletionStage<?> onBinary(WebSocket ws, ByteBuffer data, boolean last) {
            ws.request(1);
            return null;
        }

        @Override
        public CompletionStage<?> onClose(WebSocket ws, int statusCode, String reason) {
            // TODO: Replace blocking recursive reconnects with scheduled backoff and heartbeat monitoring.
            log.warn("OneBot WebSocket 断开: code={}, reason={}", statusCode, reason);
            buffer.setLength(0);
            if (running) {
                log.info("将在 5 秒后重连...");
                try { Thread.sleep(5_000); } catch (InterruptedException ignored) {}
                if (running) connect();
            }
            return null;
        }

        @Override
        public void onError(WebSocket ws, Throwable error) {
            log.error("OneBot WebSocket 错误: {}", error.getMessage());
        }
    }

    // ========================================================================
    //  事件处理
    // ========================================================================

    private void handleInbound(String json) {
        try {
            JsonNode root = objectMapper.readTree(json);

            // 捕获机器人的 QQ 号
            if (selfId.isEmpty() && root.has("self_id")) {
                selfId = root.get("self_id").asText();
                log.info("获取到机器人 QQ: {}", selfId);
            }

            String postType = root.has("post_type") ? root.get("post_type").asText() : "";
            if (!"message".equals(postType)) return;

            String messageType = root.has("message_type") ? root.get("message_type").asText() : "";
            String userId = root.has("user_id") ? root.get("user_id").asText() : "";
            String rawMessage = root.has("raw_message") ? root.get("raw_message").asText() : "";

            if (rawMessage.isEmpty()) return;

            if ("group".equals(messageType)) {
                String groupId = root.has("group_id") ? root.get("group_id").asText() : "";
                handleGroupMessage(groupId, userId, rawMessage);
            } else if ("private".equals(messageType)) {
                handlePrivateMessage(userId, rawMessage);
            }

        } catch (Exception e) {
            log.error("解析 OneBot 事件 JSON 失败", e);
        }
    }

    // ========================================================================
    //  群聊消息处理
    // ========================================================================

    private void handleGroupMessage(String groupId, String qqNumber, String rawMessage) {
        String cleanMessage = stripAtMention(rawMessage);
        if (cleanMessage.isEmpty()) return;

        log.info("QQ group message: group={}, qq={}, hasImage={}, messageLen={}",
                groupId, qqNumber,
                cqImageMessageParser.extractImageUrl(cleanMessage).isPresent(),
                cleanMessage.length());

        QqContext ctx = resolveQqContext(qqNumber, groupId);
        String historyKey = "group:" + groupId + ":" + qqNumber;

        String sessionId = "qq-g-" + groupId + "-" + qqNumber;
        AgentExecutionContext executionContext = executionContextFactory.create(
                sessionId, ctx.userId, ctx.courseId, AgentChannel.QQ_GROUP);
        agentLoopService.registerSessionContext(executionContext);

        String contextMessage;

        try {
            PreparedVisualMessage prepared = prepareVisualMessage(cleanMessage);
            contextMessage = buildContextMessage(historyKey, prepared.agentMessage());
            String reply = agentLoopService.chat(contextMessage, executionContext, null);
            if (reply != null && !reply.isBlank()) {
                String cleanReply = stripMarkdown(reply);
                saveHistory(historyKey, prepared.historyMessage(), cleanReply);
                sendGroupReply(groupId, qqNumber, cleanReply);
            }
        } catch (Exception e) {
            log.error("QQ群消息 AI 处理失败: qq={}", qqNumber, e);
            sendGroupMessage(groupId,
                    "[CQ:at,qq=" + qqNumber + "] 抱歉，处理你的问题时出错了，请稍后重试。");
        }
    }

    // ========================================================================
    //  私聊消息处理
    // ========================================================================

    private void handlePrivateMessage(String qqNumber, String rawMessage) {
        String cleanMessage = rawMessage.trim();
        if (cleanMessage.isEmpty()) return;

        log.info("QQ private message: qq={}, hasImage={}, messageLen={}",
                qqNumber,
                cqImageMessageParser.extractImageUrl(cleanMessage).isPresent(),
                cleanMessage.length());

        QqContext ctx = resolveQqContext(qqNumber, null);
        String historyKey = "private:" + qqNumber;

        String sessionId = "qq-p-" + qqNumber;
        AgentExecutionContext executionContext = executionContextFactory.create(
                sessionId, ctx.userId, ctx.courseId, AgentChannel.QQ_PRIVATE);
        agentLoopService.registerSessionContext(executionContext);

        String contextMessage;

        try {
            PreparedVisualMessage prepared = prepareVisualMessage(cleanMessage);
            contextMessage = buildContextMessage(historyKey, prepared.agentMessage());
            String reply = agentLoopService.chat(contextMessage, executionContext, null);
            if (reply != null && !reply.isBlank()) {
                String cleanReply = stripMarkdown(reply);
                saveHistory(historyKey, prepared.historyMessage(), cleanReply);
                sendPrivateReply(qqNumber, cleanReply);
            }
        } catch (Exception e) {
            log.error("QQ私聊 AI 处理失败: qq={}", qqNumber, e);
            sendPrivateMessage(qqNumber, "抱歉，处理你的问题时出错了，请稍后重试。");
        }
    }

    // ========================================================================
    //  QQ 上下文解析
    // ========================================================================

    private PreparedVisualMessage prepareVisualMessage(String message) {
        var imageUrl = cqImageMessageParser.extractImageUrl(message);
        if (imageUrl.isEmpty()) {
            return new PreparedVisualMessage(message, message);
        }

        String question = cqImageMessageParser.stripImages(message);
        if (question.isBlank()) {
            question = "请分析这张图片。";
        }

        VisualAsset asset = visualAssetService.importUrl(imageUrl.get());
        VisualObservation observation = visionUnderstandingService.analyze(
                asset.assetId(), VisionTask.DESCRIBE, question);

        String agentMessage = """
                用户发送了一张图片。图片已经由系统视觉模块完成分析，禁止根据文件名或 URL 猜测。
                assetId: %s
                视觉分析结果:
                %s

                用户问题:
                %s
                """.formatted(asset.assetId(), observation.summary(), question);
        return new PreparedVisualMessage(agentMessage, "[图片] " + question);
    }

    private QqContext resolveQqContext(String qqNumber, String groupId) {
        return groupId != null ? resolveGroupContext(qqNumber, groupId) : resolvePrivateContext(qqNumber);
    }

    private QqContext resolveGroupContext(String qqNumber, String groupId) {
        QqContext ctx = new QqContext();
        try {
            ClassInfo cls = classInfoMapper.selectByQqGroupId(groupId);
            if (cls != null && cls.getTeacherId() != null) {
                ctx.userId = cls.getTeacherId();
                ctx.courseId = cls.getCourseId();
                log.debug("群聊上下文(群号): groupId={}, courseId={}", groupId, ctx.courseId);
                return ctx;
            }
        } catch (Exception e) {
            log.debug("群号解析上下文失败: groupId={}", groupId, e);
        }
        try {
            String studentId = studentQqBindingMapper.selectStudentIdByQq(qqNumber);
            if (studentId != null) {
                ClassStudent cs = classStudentMapper.selectByStudentId(studentId);
                if (cs != null && cs.getClassId() != null) {
                    ClassInfo cls = classInfoMapper.selectById(cs.getClassId());
                    if (cls != null && cls.getTeacherId() != null) {
                        ctx.userId = cls.getTeacherId();
                        ctx.courseId = cls.getCourseId();
                        log.debug("群聊上下文(QQ兜底): qq={}, courseId={}", qqNumber, ctx.courseId);
                    }
                }
            }
        } catch (Exception e) {
            log.debug("QQ号解析上下文失败: qq={}", qqNumber, e);
        }
        return ctx;
    }

    private QqContext resolvePrivateContext(String qqNumber) {
        QqContext ctx = new QqContext();
        try {
            String studentId = studentQqBindingMapper.selectStudentIdByQq(qqNumber);
            if (studentId != null) {
                ClassStudent cs = classStudentMapper.selectByStudentId(studentId);
                if (cs != null && cs.getClassId() != null) {
                    ClassInfo cls = classInfoMapper.selectById(cs.getClassId());
                    if (cls != null && cls.getTeacherId() != null) {
                        ctx.userId = cls.getTeacherId();
                        ctx.courseId = cls.getCourseId();
                    }
                }
            }
        } catch (Exception e) {
            log.debug("QQ号解析上下文失败: qq={}", qqNumber, e);
        }
        return ctx;
    }

    // ========================================================================
    //  对话历史管理
    // ========================================================================

    private String buildContextMessage(String historyKey, String currentMsg) {
        List<Map<String, String>> history = conversationCache.getIfPresent(historyKey);
        if (history == null || history.isEmpty()) return currentMsg;

        StringBuilder sb = new StringBuilder();
        sb.append("【以下为最近对话记录】\n");
        for (Map<String, String> turn : history) {
            sb.append("学生: ").append(turn.get("user")).append("\n");
            sb.append("助手: ").append(turn.get("assistant")).append("\n");
        }
        sb.append("【对话记录结束】\n");
        sb.append("当前问题: ").append(currentMsg);
        return sb.toString();
    }

    private void saveHistory(String historyKey, String userMsg, String assistantMsg) {
        List<Map<String, String>> history = conversationCache.get(historyKey, k -> new ArrayList<>());
        if (history == null) history = new ArrayList<>();
        history.add(Map.of("user", truncateMsg(userMsg), "assistant", truncateMsg(assistantMsg)));
        while (history.size() > MAX_HISTORY) history.remove(0);
        conversationCache.put(historyKey, history);
    }

    private String truncateMsg(String msg) {
        if (msg == null) return "";
        return msg.length() > 200 ? msg.substring(0, 197) + "..." : msg;
    }

    // ========================================================================
    //  工具方法
    // ========================================================================

    private String stripAtMention(String raw) {
        if (raw == null) return "";
        if (!raw.contains("[CQ:at,qq=")) return "";
        String cleaned = raw.replaceAll("\\[CQ:at,[^]]+\\]", "").trim();
        cleaned = cleaned.replaceFirst("^@\\S+\\s*", "").trim();
        return cleaned;
    }

    /**
     * 剥离 Markdown 格式符号，适配 QQ 纯文本环境。
     * QQ 文本框不支持 Markdown 渲染，保留符号反而影响阅读体验。
     */
    private String stripMarkdown(String text) {
        if (text == null || text.isEmpty()) return text;

        // 粗体/斜体/粗斜体
        text = text.replaceAll("\\*\\*\\*(.+?)\\*\\*\\*", "$1");
        text = text.replaceAll("\\*\\*(.+?)\\*\\*", "$1");
        text = text.replaceAll("(?<!\\*)\\*(?!\\*)(.+?)(?<!\\*)\\*(?!\\*)", "$1");
        text = text.replaceAll("___(.+?)___", "$1");
        text = text.replaceAll("__(.+?)__", "$1");

        // 行内代码（保留代码内容）
        text = text.replaceAll("`([^`]+)`", "$1");

        // 删除线
        text = text.replaceAll("~~(.+?)~~", "$1");

        // Markdown 链接：[text](url) → text(url)
        text = text.replaceAll("\\[([^]]+)]\\(([^)]+)\\)", "$1($2)");

        // 图片语法：![alt](url) → [图片]
        text = text.replaceAll("!\\[[^]]*]\\([^)]+\\)", "[图片]");

        // 标题：去掉行首 # 符号
        text = text.replaceAll("(?m)^#{1,6}\\s+", "");

        // 无序列表：保留缩进和内容，- 替换为 ·
        text = text.replaceAll("(?m)^[\\s]*[-*]\\s+", "· ");

        // 有序列表：去掉序号前缀（保留内容）
        text = text.replaceAll("(?m)^\\d+\\.\\s+", "");

        // 引用：去掉 > 符号
        text = text.replaceAll("(?m)^>\\s+", "");

        // 水平线：替换为简短分隔符
        text = text.replaceAll("(?m)^[-*_]{3,}\\s*$", "────────");

        // 代码块标记：```language 和 ```
        text = text.replaceAll("```[a-zA-Z]*\\s*", "");

        // 多余空行压缩（最多保留一个空行）
        text = text.replaceAll("\n{3,}", "\n\n");

        return text.trim();
    }

    private record PreparedVisualMessage(String agentMessage, String historyMessage) {
    }

    private static class QqContext {
        Long userId;
        Long courseId;
    }
}
