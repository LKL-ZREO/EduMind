package com.firedemo.demo.rag;

import lombok.Builder;
import lombok.Data;

import java.util.Collections;
import java.util.List;

/**
 * RAG 统一检索结果
 */
@Data
@Builder
public class RagResult {

    /** QQ Bot 格式：原始消息 + 【相关知识库内容】 */
    @Builder.Default
    private String enhancedMessage = "";

    /** MCP 工具格式：带文档名和来源的格式化文本 */
    @Builder.Default
    private String formattedContent = "";

    /** 是否检索到相关内容 */
    @Builder.Default
    private boolean hasContext = false;

    /** 原始检索结果（供代码使用，如 DocumentServiceImpl） */
    @Builder.Default
    private List<RrfFusionService.ScoredChunk> results = Collections.emptyList();

    /** 诊断 Trace */
    private RagTrace trace;

    /** 检索耗时（毫秒） */
    @Builder.Default
    private long elapsedMs = 0;

    /** 是否使用了 LLM 改写 */
    @Builder.Default
    private boolean queryRewritten = false;

    /** 改写后的 query（如果触发过改写） */
    private String rewrittenQuery;

    // ====== 工厂方法 ======

    /** 空结果（无上下文） */
    public static RagResult empty(String originalQuery, RagTrace trace, long elapsedMs) {
        return RagResult.builder()
                .enhancedMessage(originalQuery)
                .formattedContent("知识库中未找到相关内容。")
                .hasContext(false)
                .trace(trace)
                .elapsedMs(elapsedMs)
                .build();
    }
}
