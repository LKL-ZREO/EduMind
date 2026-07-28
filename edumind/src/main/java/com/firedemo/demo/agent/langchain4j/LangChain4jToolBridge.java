package com.firedemo.demo.agent.langchain4j;

import com.firedemo.demo.agent.context.AgentExecutionContext;
import com.firedemo.demo.agent.context.AgentRunTrace;
import com.firedemo.demo.agent.observability.AgentToolMetrics;
import com.firedemo.demo.mcp.ToolDefinition;
import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import dev.langchain4j.invocation.InvocationParameters;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * LangChain4j {@link Tool @Tool} 桥接 — 将现有的 {@link ToolDefinition} Bean 包装为
 * LangChain4j 框架可识别的工具方法。
 *
 * <p><b>设计意图：</b>
 * {@link ToolDefinition} 同时服务于 MCP 端点和 Agent 循环，保持单一真相源。
 * 此类提供 {@code @Tool} 注解的方法，由 LangChain4j 自动生成 JSON Schema 并执行工具调用循环，
 * 替代原来手写的 {@code buildToolSchemas()} 和 {@code executeTool()}。
 *
 * <p><b>上下文传递：</b>应用通过 LangChain4j 原生 {@link InvocationParameters}
 * 传入可信执行上下文。该参数不会出现在模型可见的工具参数 Schema 中。
 */
@Slf4j
@Component
public class LangChain4jToolBridge {

    private final Map<String, ToolDefinition> toolMap;
    private final AgentToolMetrics toolMetrics;

    public LangChain4jToolBridge(List<ToolDefinition> toolDefinitions,
                                 AgentToolMetrics toolMetrics) {
        this.toolMap = toolDefinitions.stream()
                .collect(Collectors.toMap(ToolDefinition::name, t -> t));
        this.toolMetrics = toolMetrics;
        log.info("LangChain4j ToolBridge 初始化: 已注册 {} 个工具 — {}",
                toolDefinitions.size(),
                toolDefinitions.stream().map(ToolDefinition::name).toList());
    }

    // ========================================================================
    //  @Tool 方法 — 每个方法对应一个 ToolDefinition
    // ========================================================================

    @Tool("从教学知识库（RAG）中检索C语言课程相关内容，包括教材、讲义、编程规范、知识点讲解等。"
            + "当用户询问C语言语法、概念、原理等专业知识时必须调用。"
            + "参数：query=搜索关键词，topK=返回结果数(默认3,最大10)")
    public String searchKnowledge(
            @P("搜索关键词或问题，越具体越好") String query,
            @P("返回结果数量，默认3，最大10") Integer topK,
            InvocationParameters invocationParameters) {

        Map<String, Object> args = new HashMap<>();
        args.put("query", query);
        if (topK != null) args.put("topK", topK);
        return execute("searchKnowledge", args, invocationParameters);
    }

    @Tool("分析视觉资产。优先使用 assetId；task 支持 describe/ocr/table/formula/code/homework。"
            + "兼容旧调用：sourceType=url/base64/cq，source=图片来源。"
            + "如果视觉结果涉及课程知识点，再调用 searchKnowledge。")
    public String analyzeVisualContent(
            @P(value = "视觉资产 ID，优先使用", required = false) String assetId,
            @P(value = "任务类型：describe/ocr/table/formula/code/homework", required = false) String task,
            @P(value = "兼容参数：url/base64/cq", required = false) String sourceType,
            @P(value = "兼容参数：图片 URL、base64、data URL 或 CQ 图片消息", required = false) String source,
            @P(value = "用户关于视觉资产的问题", required = false) String question,
            InvocationParameters invocationParameters) {

        Map<String, Object> args = new HashMap<>();
        if (assetId != null) args.put("assetId", assetId);
        if (task != null) args.put("task", task);
        if (sourceType != null) args.put("sourceType", sourceType);
        if (source != null) args.put("source", source);
        if (question != null) args.put("question", question);
        return execute("analyzeVisualContent", args, invocationParameters);
    }

    @Tool("查询班级的整体学习情况，包括学生人数、作业总数、平均分、薄弱知识点（Top5）。参数：className=班级名称")
    public String queryClassStatus(
            @P("班级名称") String className,
            InvocationParameters invocationParameters) {
        return execute("queryClassStatus", Map.of("className", className), invocationParameters);
    }

    @Tool("查询班级的作业任务列表，包括作业名称、描述、截止时间和状态（进行中/已截止）。参数：className=班级名称")
    public String queryHomeworkTasks(
            @P("班级名称") String className,
            InvocationParameters invocationParameters) {
        return execute("queryHomeworkTasks", Map.of("className", className), invocationParameters);
    }

    @Tool("查询单个学生的作业成绩统计，包括提交次数、平均分、最近成绩、成绩趋势、薄弱知识点。参数：studentName=学生姓名")
    public String queryStudentStats(
            @P("学生姓名") String studentName,
            InvocationParameters invocationParameters) {
        return execute("queryStudentStats", Map.of("studentName", studentName), invocationParameters);
    }

    @Tool("获取当前日期和时间，格式为 yyyy-MM-dd HH:mm:ss")
    public String getCurrentTime(InvocationParameters invocationParameters) {
        return execute("getCurrentTime", Map.of(), invocationParameters);
    }

    // ========================================================================
    //  内部方法
    // ========================================================================

    private String execute(String toolName,
                           Map<String, Object> args,
                           InvocationParameters invocationParameters) {
        ToolDefinition tool = toolMap.get(toolName);
        if (tool == null) {
            return "未知工具: " + toolName + "，可用工具: " + toolMap.keySet();
        }
        AgentExecutionContext context = AgentInvocationParameters.requireContext(invocationParameters);
        AgentRunTrace trace = AgentInvocationParameters.requireTrace(invocationParameters);
        Map<String, Object> toolArgs = args;
        args = summarizeArgs(args);
        log.info("Tool call: name={}", toolName);
        try {
            log.debug("ToolBridge 执行: name={}, args={}", toolName, args);
            return toolMetrics.record(toolName,
                    () -> trace.traceToolCall(toolName, () -> tool.execute(toolArgs, context)));
        } catch (Exception e) {
            log.error("ToolBridge 工具执行失败: name={}", toolName, e);
            return "工具执行出错: " + e.getMessage();
        }
    }

    private Map<String, Object> summarizeArgs(Map<String, Object> args) {
        Map<String, Object> summary = new HashMap<>();
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
}
