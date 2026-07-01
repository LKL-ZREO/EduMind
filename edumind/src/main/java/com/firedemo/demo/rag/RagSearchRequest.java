package com.firedemo.demo.rag;

import lombok.Builder;
import lombok.Data;

import java.util.Map;
import java.util.Set;

/**
 * RAG 统一检索请求
 * <p>
 * 三个调用入口：
 * <ul>
 *   <li>QQ Bot (OnebotRagController) → {@code userId} / {@code accessibleKbIds} 由调用方提前解析</li>
 *   <li>MCP Tool (KnowledgeSearchTool) → 同上，通过 session/groupId/qq 解析</li>
 *   <li>Web Chat (DocumentServiceImpl) → 同上</li>
 * </ul>
 * <p>
 * 权限上下文（userId + accessibleKbIds）由<b>调用方</b>解析好传入，
 * RagService 不关心"QQ → 学生 → 班级 → 老师"这条链。
 */
@Data
@Builder
public class RagSearchRequest {

    /** 原始查询（学生原话，不改写） */
    private String query;

    /** 返回条数，默认 3 */
    @Builder.Default
    private int topK = 3;

    /** 当前教师/上传者 ID，用于私人文档权限过滤 */
    private Long userId;

    /** 用户可访问的共享知识库 ID 集合 */
    private Set<Long> accessibleKbIds;

    /** 课程 ID，用于构建课程上下文头部 */
    private Long courseId;

    /** 是否启用 Reranker 精排（Web/MCP 端 true，QQ 端可根据需要调整） */
    @Builder.Default
    private boolean enableReranker = true;

    /** 会话 ID，供 Query Rewrite 时做多轮指代消解 */
    private String sessionId;

    /** 返回格式偏好 */
    @Builder.Default
    private Format format = Format.ENHANCED_MESSAGE;

    public enum Format {
        /** QQ Bot 格式：原始消息 + 【相关知识库内容】 */
        ENHANCED_MESSAGE,
        /** MCP 工具格式：带文档名和来源的格式化文本 */
        FORMATTED_CONTENT,
        /** 原始结果列表：供代码使用 */
        RAW_RESULTS
    }
}
