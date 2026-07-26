package com.firedemo.demo.mcp.tools;

import com.firedemo.demo.agent.context.AgentExecutionContext;
import com.firedemo.demo.mcp.ToolDefinition;
import com.firedemo.demo.rag.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * MCP 工具：知识库 RAG 检索（委托 RagService）
 *
 * <pre>
 *   检索链路：Embedding → pgvector + ILIKE（双路） → RRF 融合 → Reranker 精排
 *   LLM 自主判断是否调用此工具，实现 Agentic RAG。
 * </pre>
 */
@Slf4j
@Component
public class KnowledgeSearchTool implements ToolDefinition {

    private final RagService ragService;

    public KnowledgeSearchTool(RagService ragService) {
        this.ragService = ragService;
    }

    @Override
    public String name() {
        return "searchKnowledge";
    }

    @Override
    public String description() {
        return """
                从教学知识库（RAG）中检索C语言课程相关内容，包括教材、讲义、编程规范、知识点讲解等。

                适用场景（必须调用）：
                - 用户询问C语言语法、概念、原理等专业知识
                - 用户要求解释编程术语或技术细节
                - 用户的问题涉及课程内容、作业要求、考试范围
                - 用户问"怎么学""什么是""如何理解"等知识性问题
                - 任何你记忆不确定的技术问题

                不适用场景：
                - 简单问候、闲聊、感谢
                - 查询班级/学生实时数据（用 queryClassStatus / queryStudentStats）
                - 查询作业任务列表（用 queryHomeworkTasks）

                参数说明：
                - query: 从用户问题中提取的核心关键词，越具体越好（如"指针数组区别"而非"指针的问题"）
                - topK: 返回结果数，默认3即可，需要更多信息时设为5-8""";
    }

    @Override
    public Map<String, Object> inputSchema() {
        return Map.of(
                "type", "object",
                "properties", Map.of(
                        "query", Map.of("type", "string", "description", "搜索关键词或问题，越具体越好"),
                        "topK", Map.of("type", "integer", "description", "返回结果数量，默认3，最大10")
                ),
                "required", List.of("query")
        );
    }

    @Override
    public String execute(Map<String, Object> arguments, AgentExecutionContext context) {
        String query = (String) arguments.get("query");
        int topK = Math.max(1, Math.min(10, getInt(arguments, "topK", 3)));

        log.info("MCP Tool searchKnowledge: query={}, topK={}", query, topK);

        if (!context.isAuthenticated()) {
            return "无法访问知识库：当前会话未认证或已过期。";
        }

        try {
            RagSearchRequest request = RagSearchRequest.builder()
                    .query(query)
                    .topK(topK)
                    .userId(context.userId())
                    .accessibleKbIds(context.accessibleKbIds())
                    .courseId(context.courseId())
                    .enableReranker(true)
                    .sessionId(context.sessionId())
                    .format(RagSearchRequest.Format.FORMATTED_CONTENT)
                    .build();

            RagResult result = ragService.search(request);

            if (!result.isHasContext()) {
                return result.getFormattedContent();
            }

            return result.getFormattedContent();

        } catch (Exception e) {
            log.error("知识库搜索失败", e);
            return "搜索知识库时出错: " + e.getMessage();
        }
    }

    private int getInt(Map<String, Object> args, String key, int defaultValue) {
        Object val = args.get(key);
        if (val instanceof Number n) return n.intValue();
        if (val instanceof String s) {
            try { return Integer.parseInt(s); } catch (NumberFormatException ignored) {}
        }
        return defaultValue;
    }
}
