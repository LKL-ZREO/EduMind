package com.firedemo.demo.mcp.tools;

import com.firedemo.demo.Entity.ClassInfo;
import com.firedemo.demo.Entity.ClassStudent;
import com.firedemo.demo.Entity.Course;
import com.firedemo.demo.Entity.DocumentChunk;
import com.firedemo.demo.Service.CourseService;
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
import java.util.stream.Collectors;

/**
 * MCP 工具：知识库 RAG 检索（全管线版）
 * <pre>
 *   检索链路：Embedding → pgvector + ILIKE → RRF 融合 → Reranker 精排
 * </pre>
 * LLM 自主判断是否调用此工具，实现 Agentic RAG。
 */
@Slf4j
@Component
public class KnowledgeSearchTool implements ToolDefinition {

    private final EmbeddingService embeddingService;
    private final VectorStoreService vectorStoreService;
    private final RrfFusionService rrfFusionService;
    private final RerankerService rerankerService;
    private final QueryRewriter queryRewriter;
    private final McpSessionStore mcpSessionStore;
    private final ClassInfoMapper classInfoMapper;
    private final StudentQqBindingMapper studentQqBindingMapper;
    private final ClassStudentMapper classStudentMapper;
    private final SharedKbMemberMapper sharedKbMemberMapper;
    private final CourseService courseService;

    public KnowledgeSearchTool(EmbeddingService embeddingService,
                               VectorStoreService vectorStoreService,
                               RrfFusionService rrfFusionService,
                               RerankerService rerankerService,
                               QueryRewriter queryRewriter,
                               McpSessionStore mcpSessionStore,
                               ClassInfoMapper classInfoMapper,
                               StudentQqBindingMapper studentQqBindingMapper,
                               ClassStudentMapper classStudentMapper,
                               SharedKbMemberMapper sharedKbMemberMapper,
                               CourseService courseService) {
        this.embeddingService = embeddingService;
        this.vectorStoreService = vectorStoreService;
        this.rrfFusionService = rrfFusionService;
        this.rerankerService = rerankerService;
        this.queryRewriter = queryRewriter;
        this.mcpSessionStore = mcpSessionStore;
        this.classInfoMapper = classInfoMapper;
        this.studentQqBindingMapper = studentQqBindingMapper;
        this.classStudentMapper = classStudentMapper;
        this.sharedKbMemberMapper = sharedKbMemberMapper;
        this.courseService = courseService;
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
        log.debug("MCP Tool searchKnowledge diag: sessionId={}, groupId={}, userId={}",
                arguments.get("sessionId"), arguments.get("groupId"), arguments.get("userId"));

        try {
            RagTrace trace = new RagTrace(query);

            // ① Query Rewrite
            trace.step("rewrite");
            query = queryRewriter.rewrite(query);
            trace.endStep(query);

            // ② Embedding
            trace.step("embed");
            float[] queryEmbedding = embeddingService.embedQuery(query);
            trace.endStep();

            // 获取当前用户上下文（多路径解析，优先级递减）
            ToolContext ctx = ToolContextHolder.get();                          // ① McpController ThreadLocal
            if (ctx == null) {
                String sid = (String) arguments.get("sessionId");
                if (sid != null && !sid.isEmpty()) {
                    ctx = mcpSessionStore.get(sid);                            // ② ChatController Redis
                }
            }
            if (ctx == null) {
                ctx = resolveByGroupId((String) arguments.get("groupId"));    // ③ QQ群 → 班级 → 老师
            }
            if (ctx == null) {
                ctx = resolveByQq((String) arguments.get("userId"));          // ④ QQ号 → 学生 → 班级 → 老师
            }
            Long userId = ctx != null ? ctx.getUserId() : null;
            Set<Long> accessibleKbIds = ctx != null ? ctx.getAccessibleKbIds() : null;
            log.info("RAG filter: userId={}, kbIds={}", userId, accessibleKbIds);

            // ③ 双路并行检索（带权限过滤）
            trace.step("vector");
            List<DocumentChunk> vectorResults = vectorStoreService.similaritySearch(
                    queryEmbedding, topK * 2, userId, accessibleKbIds);
            trace.set("vectorHits", vectorResults.size()).endStep();

            trace.step("keyword");
            List<VectorStoreService.ScoredChunk> keywordScored = vectorStoreService.keywordSearch(
                    query, topK * 2, userId, accessibleKbIds);
            List<DocumentChunk> keywordResults = keywordScored.stream()
                    .map(VectorStoreService.ScoredChunk::chunk)
                    .collect(Collectors.toList());
            trace.set("keywordHits", keywordResults.size()).endStep();

            if (vectorResults.isEmpty() && keywordResults.isEmpty()) {
                log.info("RAG Trace: {}", trace.finish(0));
                return "知识库中未找到与「" + query + "」相关的内容。";
            }

            // ④ RRF 融合
            trace.step("rrf");
            List<RrfFusionService.ScoredChunk> fused = rrfFusionService.fuse(vectorResults, keywordResults);
            trace.set("rrfFused", fused.size()).endStep();

            // ⑤ Reranker 精排
            trace.step("reranker");
            List<RrfFusionService.ScoredChunk> reranked = rerankerService.rerank(query, fused, topK, trace);
            trace.endStep();

            log.info("RAG Trace: {}", trace.finish(0));

            // ⑥ 构建课程上下文头部（告诉 Jarvis 当前是哪个课程）
            String courseHeader = buildCourseHeader(ctx);

            // ⑦ 格式化输出
            String body = reranked.stream()
                    .map(sc -> {
                        DocumentChunk chunk = sc.chunk();
                        String docName = chunk.getDocumentName();
                        String content = chunk.getContent();
                        String truncated = content.length() > 500
                                ? content.substring(0, 500) + "…"
                                : content;
                        return (docName != null ? "【" + docName + "】\n" : "") + truncated;
                    })
                    .collect(Collectors.joining("\n\n---\n\n"));

            return courseHeader + body;

        } catch (Exception e) {
            log.error("知识库搜索失败", e);
            return "搜索知识库时出错: " + e.getMessage();
        }
    }

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
                Long courseId = cls.getCourseId();
                log.debug("GroupId resolve: groupId={}, class={}, teacher={}, kbIds={}, courseId={}",
                        groupId, cls.getName(), teacherId, kbIds != null ? kbIds.size() : 0, courseId);
                return new ToolContext(teacherId, kbIds, courseId);
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
                        Long courseId = cls.getCourseId();
                        log.debug("QQ resolve: qq={}, student={}, class={}, teacher={}, kbIds={}, courseId={}",
                                qqNumber, studentId, cls.getName(), teacherId, kbIds != null ? kbIds.size() : 0, courseId);
                        return new ToolContext(teacherId, kbIds, courseId);
                    }
                }
            }
        } catch (Exception e) {
            log.warn("通过QQ号解析上下文失败: qqNumber={}", qqNumber, e);
        }
        return null;
    }

    /**
     * 构建课程上下文头部，注入到检索结果中，让 Jarvis 知道当前是哪个课程。
     */
    private String buildCourseHeader(ToolContext ctx) {
        if (ctx == null || ctx.getCourseId() == null) return "";
        try {
            Course course = courseService.getById(ctx.getCourseId());
            if (course != null) {
                String header = String.format(
                        "\n【系统提示】当前对话所属课程：%s。",
                        course.getName());
                if (course.getKnowledgeScope() != null && !course.getKnowledgeScope().isEmpty()) {
                    header += String.format("知识范围：%s。", course.getKnowledgeScope());
                }
                header += "请以此课程助教身份回答问题。\n\n";
                return header;
            }
        } catch (Exception e) {
            log.warn("构建课程上下文头部失败: courseId={}", ctx.getCourseId(), e);
        }
        return "";
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
