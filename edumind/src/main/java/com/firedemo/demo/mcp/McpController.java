package com.firedemo.demo.mcp;

import com.firedemo.demo.agent.context.AgentChannel;
import com.firedemo.demo.agent.context.AgentExecutionContext;
import com.firedemo.demo.agent.context.AgentExecutionContextFactory;
import com.firedemo.demo.agent.context.AgentRunTrace;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import jakarta.servlet.http.HttpServletRequest;

/**
 * MCP JSON-RPC 端点
 * <p>
 * 实现 MCP Streamable HTTP 传输协议，无需额外依赖。
 * OpenClaw 通过 {@code openclaw mcp set} 注册此端点后，Agent 可自动发现并调用工具。
 * <p>
 * 支持的 MCP 方法：
 * <ul>
 *   <li>initialize — 握手，返回服务器能力</li>
 *   <li>notifications/initialized — 客户端就绪通知（无响应）</li>
 *   <li>tools/list — 列出所有可用工具</li>
 *   <li>tools/call — 调用指定工具</li>
 * </ul>
 */
@Slf4j
@RestController
@RequestMapping("/mcp")
public class McpController {

    private final Map<String, ToolDefinition> tools = new LinkedHashMap<>();
    private final McpSessionStore mcpSessionStore;
    private final AgentExecutionContextFactory executionContextFactory;

    /** MCP 协议 JSON-RPC 响应键 */
    private static final String KEY_CONTENT = "content";
    private static final String KEY_TYPE = "type";
    private static final String KEY_TEXT = "text";

    public McpController(List<ToolDefinition> toolDefinitions,
                         McpSessionStore mcpSessionStore,
                         AgentExecutionContextFactory executionContextFactory) {
        for (ToolDefinition tool : toolDefinitions) {
            tools.put(tool.name(), tool);
        }
        this.mcpSessionStore = mcpSessionStore;
        this.executionContextFactory = executionContextFactory;
    }

    @PostConstruct
    public void init() {
        log.info("MCP Server 已启动，注册 {} 个工具: {}",
                tools.size(), tools.keySet());
    }

    /**
     * MCP 主入口 — 所有 JSON-RPC 请求走这里
     */
    @PostMapping(produces = "application/json", consumes = "application/json")
    public Map<String, Object> handle(@RequestBody Map<String, Object> request,
                                      HttpServletRequest httpRequest) {

        String method = (String) request.get("method");
        Object id = request.get("id");
        @SuppressWarnings("unchecked")
        Map<String, Object> params = (Map<String, Object>) request.getOrDefault("params", new HashMap<>());

        log.info("MCP X-MCP-API-Key present={}", httpRequest.getHeader("X-MCP-API-Key") != null);
        log.debug("MCP request: method={}, id={}", method, id);
        if ("tools/call".equals(method)) {
            log.debug("MCP tools/call raw params keys: {}, headers: X-Session-Id={}",
                    params.keySet(), httpRequest.getHeader("X-Session-Id"));
        }

        try {
            return switch (method) {
                case "initialize" -> handleInitialize(id, params);
                case "notifications/initialized" -> null; // 通知不需要响应
                case "tools/list" -> handleToolsList(id);
                case "tools/call" -> handleToolsCall(id, params, httpRequest);
                default -> errorResponse(id, -32601, "未知方法: " + method);
            };
        } catch (RuntimeException e) {
            log.error("MCP 处理异常", e);
            return errorResponse(id, -32603, "服务内部错误: " + e.getMessage());
        }
    }

    // ==================== MCP 方法实现 ====================

    /**
     * 初始化握手
     */
    private Map<String, Object> handleInitialize(Object id, Map<String, Object> params) {
        log.info("MCP initialize: clientInfo={}", params.get("clientInfo"));

        return successResponse(id, Map.of(
                "protocolVersion", "2024-11-05",
                "capabilities", Map.of(
                        "tools", Map.of("listChanged", false)
                ),
                "serverInfo", Map.of(
                        "name", "edu-ai-mcp-server",
                        "version", "1.0.0"
                )
        ));
    }

    /**
     * 列出所有工具
     */
    private Map<String, Object> handleToolsList(Object id) {
        List<Map<String, Object>> toolList = tools.values().stream()
                .map(tool -> {
                    Map<String, Object> t = new LinkedHashMap<>();
                    t.put("name", tool.name());
                    t.put("description", tool.description());
                    t.put("inputSchema", tool.inputSchema());
                    return t;
                })
                .collect(Collectors.toList());

        log.debug("tools/list: 返回 {} 个工具", toolList.size());

        return successResponse(id, Map.of("tools", toolList));
    }

    /**
     * 调用指定工具
     */
    private Map<String, Object> handleToolsCall(Object id,
                                                Map<String, Object> params,
                                                HttpServletRequest httpRequest) {
        String toolName = (String) params.get("name");
        @SuppressWarnings("unchecked")
        Map<String, Object> arguments = (Map<String, Object>) params.getOrDefault("arguments", Map.of());

        log.info("MCP tools/call: name={}, args={}", toolName, summarizeArgs(arguments));
        log.debug("MCP tools/call params keys: {}", params.keySet());

        ToolDefinition tool = tools.get(toolName);
        if (tool == null) {
            return errorResponse(id, -32602, "未知工具: " + toolName);
        }

        String sessionId = extractSessionId(params, httpRequest);
        AgentExecutionContext context = sessionId != null
                ? mcpSessionStore.resolve(sessionId, AgentChannel.MCP)
                : null;
        if (context == null) {
            String anonymousSessionId = sessionId != null
                    ? sessionId
                    : "mcp-" + UUID.randomUUID().toString().replace("-", "");
            context = executionContextFactory.create(
                    anonymousSessionId, null, null, AgentChannel.MCP);
        }

        AgentRunTrace trace = new AgentRunTrace(context);
        AgentExecutionContext executionContext = context;
        String result = trace.traceToolCall(
                toolName, () -> tool.execute(arguments, executionContext));
        log.info("MCP tools/call 结果: name={}, resultLen={}, traceId={}", toolName,
                result != null ? result.length() : 0, trace.traceId());

        // MCP 要求返回 content 数组
        return successResponse(id, Map.of(
                KEY_CONTENT, List.of(Map.of(
                        KEY_TYPE, KEY_TEXT,
                        KEY_TEXT, result != null ? result : ""
                ))
        ));
    }

    /**
     * 从 MCP params 中尝试提取 sessionId。
     * OpenClaw 可能通过 _meta.sessionId 或顶层 sessionId 字段回传。
     */
    private String extractSessionId(Map<String, Object> params, HttpServletRequest request) {
        String headerSessionId = request.getHeader("X-Session-Id");
        if (headerSessionId != null && !headerSessionId.isBlank()) {
            return headerSessionId.trim();
        }
        // 直接字段
        Object sid = params.get("sessionId");
        if (sid instanceof String s && !s.isEmpty()) return s;
        sid = params.get("session_id");
        if (sid instanceof String s && !s.isEmpty()) return s;
        // _meta 子对象
        @SuppressWarnings("unchecked")
        Map<String, Object> meta = (Map<String, Object>) params.get("_meta");
        if (meta != null) {
            sid = meta.get("sessionId");
            if (sid instanceof String s && !s.isEmpty()) return s;
            sid = meta.get("session_id");
            if (sid instanceof String s && !s.isEmpty()) return s;
        }
        return null;
    }

    // ==================== JSON-RPC 响应构造 ====================

    private Map<String, Object> summarizeArgs(Map<String, Object> args) {
        Map<String, Object> summary = new LinkedHashMap<>();
        for (Map.Entry<String, Object> entry : args.entrySet()) {
            Object value = entry.getValue();
            if (value instanceof String text) {
                summary.put(entry.getKey(), summarizeText(text));
            } else {
                summary.put(entry.getKey(), value);
            }
        }
        return summary;
    }

    private String summarizeText(String text) {
        if (text == null) return null;
        if (text.startsWith("data:image/")) {
            return "[data-url length=" + text.length() + "]";
        }
        if (text.startsWith("[CQ:image")) {
            return "[cq-image length=" + text.length() + "]";
        }
        if (text.length() > 160) {
            return text.substring(0, 160) + "...(length=" + text.length() + ")";
        }
        return text;
    }

    private Map<String, Object> successResponse(Object id, Map<String, Object> result) {
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("jsonrpc", "2.0");
        response.put("id", id);
        response.put("result", result);
        return response;
    }

    private Map<String, Object> errorResponse(Object id, int code, String message) {
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("jsonrpc", "2.0");
        response.put("id", id);
        response.put("error", Map.of("code", code, "message", message));
        return response;
    }
}
