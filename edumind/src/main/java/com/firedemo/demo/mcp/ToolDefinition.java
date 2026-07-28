package com.firedemo.demo.mcp;

import com.firedemo.demo.agent.context.AgentExecutionContext;

import java.util.Map;

/**
 * MCP 工具定义 — 描述一个工具的名称、参数 schema 和执行逻辑
 */
public interface ToolDefinition {

    /** 工具名称（唯一标识） */
    String name();

    /** 工具描述（告诉 LLM 这个工具做什么） */
    String description();

    /**
     * 参数 JSON Schema（给 OpenClaw 的 function calling 用）
     * <pre>
     * {
     *   "type": "object",
     *   "properties": {
     *     "query": { "type": "string", "description": "搜索关键词" }
     *   },
     *   "required": ["query"]
     * }
     * </pre>
     */
    Map<String, Object> inputSchema();

    /** 使用应用侧生成的可信上下文执行工具。 */
    String execute(Map<String, Object> arguments, AgentExecutionContext context);
}
