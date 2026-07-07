package com.firedemo.demo.mcp.tools;

import com.firedemo.demo.Entity.ClassInfo;
import com.firedemo.demo.Entity.ClassStudent;
import com.firedemo.demo.mapper.ClassInfoMapper;
import com.firedemo.demo.mapper.ClassStudentMapper;
import com.firedemo.demo.mapper.SharedKbMemberMapper;
import com.firedemo.demo.mapper.StudentQqBindingMapper;
import com.firedemo.demo.mcp.McpSessionStore;
import com.firedemo.demo.mcp.ToolContext;
import com.firedemo.demo.mcp.ToolContextHolder;
import com.firedemo.demo.mcp.ToolDefinition;
import com.firedemo.demo.rag.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.Set;

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
    private final McpSessionStore mcpSessionStore;
    private final ClassInfoMapper classInfoMapper;
    private final StudentQqBindingMapper studentQqBindingMapper;
    private final ClassStudentMapper classStudentMapper;
    private final SharedKbMemberMapper sharedKbMemberMapper;

    public KnowledgeSearchTool(RagService ragService,
                               McpSessionStore mcpSessionStore,
                               ClassInfoMapper classInfoMapper,
                               StudentQqBindingMapper studentQqBindingMapper,
                               ClassStudentMapper classStudentMapper,
                               SharedKbMemberMapper sharedKbMemberMapper) {
        this.ragService = ragService;
        this.mcpSessionStore = mcpSessionStore;
        this.classInfoMapper = classInfoMapper;
        this.studentQqBindingMapper = studentQqBindingMapper;
        this.classStudentMapper = classStudentMapper;
        this.sharedKbMemberMapper = sharedKbMemberMapper;
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
                        "topK", Map.of("type", "integer", "description", "返回结果数量，默认3，最大10"),
                        "sessionId", Map.of("type", "string", "description", "当前会话ID，系统提示词中提供，必须传入"),
                        "groupId", Map.of("type", "string", "description", "当前群号，从对话上下文中获取。不知道填 unknown。"),
                        "userId", Map.of("type", "string", "description", "当前提问者QQ号，从对话上下文中获取。不知道填 unknown。")
                ),
                "required", List.of("query")
        );
    }

    @Override
    public String execute(Map<String, Object> arguments) {
        String query = (String) arguments.get("query");
        int topK = Math.max(1, Math.min(10, getInt(arguments, "topK", 3)));

        log.info("MCP Tool searchKnowledge: query={}, topK={}", query, topK);

        try {
            // ① 解析用户上下文（多路径，优先级递减）
            ToolContext ctx = ToolContextHolder.get();                          // McpController ThreadLocal
            if (ctx == null) {
                String sid = (String) arguments.get("sessionId");
                if (sid != null && !sid.isEmpty()) {
                    ctx = mcpSessionStore.get(sid);                            // ChatController Redis
                }
            }
            if (ctx == null) {
                ctx = resolveByGroupId((String) arguments.get("groupId"));    // QQ群 → 班级 → 老师
            }
            if (ctx == null) {
                ctx = resolveByQq((String) arguments.get("userId"));          // QQ号 → 学生 → 班级 → 老师
            }

            Long userId = ctx != null ? ctx.getUserId() : null;
            Set<Long> accessibleKbIds = ctx != null ? ctx.getAccessibleKbIds() : null;
            Long courseId = ctx != null ? ctx.getCourseId() : null;

            // ② 委托 RagService 统一检索
            RagSearchRequest request = RagSearchRequest.builder()
                    .query(query)
                    .topK(topK)
                    .userId(userId)
                    .accessibleKbIds(accessibleKbIds)
                    .courseId(courseId)
                    .enableReranker(true)
                    .sessionId((String) arguments.get("sessionId"))
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

    // ==================== 上下文解析 ====================

    /**
     * 通过 QQ群号解析上下文：群号 → 班级 → 老师 → KB权限 + 课程ID
     */
    private ToolContext resolveByGroupId(String groupId) {
        if (groupId == null || groupId.isEmpty() || "unknown".equals(groupId)) return null;
        try {
            ClassInfo cls = classInfoMapper.selectByQqGroupId(groupId);
            if (cls != null && cls.getTeacherId() != null) {
                Long teacherId = cls.getTeacherId();
                Set<Long> kbIds = sharedKbMemberMapper.selectKbIdsByUserId(teacherId);
                return new ToolContext(teacherId, kbIds, cls.getCourseId());
            }
        } catch (Exception e) {
            log.warn("通过群号解析上下文失败: groupId={}", groupId, e);
        }
        return null;
    }

    /**
     * 通过QQ号解析上下文：QQ → 学号 → 班级 → 老师 → KB权限 + 课程ID
     */
    private ToolContext resolveByQq(String qqNumber) {
        if (qqNumber == null || qqNumber.isEmpty() || "unknown".equals(qqNumber)) return null;
        try {
            String studentId = studentQqBindingMapper.selectStudentIdByQq(qqNumber);
            if (studentId != null) {
                ClassStudent cs = classStudentMapper.selectByStudentId(studentId);
                if (cs != null && cs.getClassId() != null) {
                    ClassInfo cls = classInfoMapper.selectById(cs.getClassId());
                    if (cls != null && cls.getTeacherId() != null) {
                        Long teacherId = cls.getTeacherId();
                        Set<Long> kbIds = sharedKbMemberMapper.selectKbIdsByUserId(teacherId);
                        return new ToolContext(teacherId, kbIds, cls.getCourseId());
                    }
                }
            }
        } catch (Exception e) {
            log.warn("通过QQ号解析上下文失败: qqNumber={}", qqNumber, e);
        }
        return null;
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
