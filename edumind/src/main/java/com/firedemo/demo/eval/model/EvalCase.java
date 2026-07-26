package com.firedemo.demo.eval.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 单个 RAGAS 评测用例。
 * 与 rag-eval-dataset.json 格式兼容，同时扩充了 RAGAS 需要的字段。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EvalCase {

    private int id;
    private String query;

    /** AI 生成的回答（由 RAG 管线生成的完整回答） */
    @Builder.Default
    private String answer = "";

    /** 检索到的文档片段列表（top-K chunk 的文本内容） */
    @Builder.Default
    private List<String> contexts = List.of();

    /** 参考答案 / 期望的真实答案（用于 context_recall 等指标） */
    private String groundTruth;

    // ---- 以下为 RagEvalRunner 兼容字段 ----

    /** 期望检索结果包含的关键词 */
    @Builder.Default
    private List<String> expectedKeywords = List.of();

    /** 期望检索结果包含的内容片段（用于模糊匹配） */
    @Builder.Default
    private List<String> expectedContent = List.of();

    /** 至少需要多少个 chunk 来覆盖回答 */
    private int minChunksToCover;

    /** 来源文档 ID（由数据集生成器设置） */
    private String sourceDocId;

    /** 来源文档名称（由数据集生成器设置） */
    private String sourceDocName;
}
