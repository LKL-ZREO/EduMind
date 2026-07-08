package com.firedemo.demo.rag;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * RAG 管线轻量 Trace — 记录每一步的耗时和召回数据
 * <p>
 * 用法：
 * <pre>
 *   RagTrace trace = new RagTrace(query);
 *   trace.step("rewrite");
 *   // ... rewrite ...
 *   trace.endStep(rewritten);
 *
 *   trace.step("embed").endStep();           // 记录耗时
 *   trace.set("vectorHits", 6);              // 记录数量
 *   trace.addRerankHit(docName, score);       // 记录精排结果
 *
 *   log.info(trace.finish(llmTokens));        // 结束并输出 JSON
 * </pre>
 */
@Slf4j
public class RagTrace {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final String query;
    private final long start = System.currentTimeMillis();

    private String rewritten;
    private int vectorHits;
    private int keywordHits;
    private int rrfFused;

    private final List<RerankerHit> rerankTop = new ArrayList<>();

    // 各步耗时（ms）
    private int rewriteMs;
    private int embedMs;
    private int vectorMs;
    private int keywordMs;
    private int rrfMs;
    private int rerankerMs;

    // 当前计时步骤
    private String currentStep;
    private long currentStepStart;

    public RagTrace(String query) {
        this.query = query;
    }

    /** 开始计时一个步骤 */
    public RagTrace step(String name) {
        this.currentStep = name;
        this.currentStepStart = System.currentTimeMillis();
        return this;
    }

    /** 结束当前步骤并记录结果 */
    public RagTrace endStep() {
        endStep(null);
        return this;
    }

    /** 结束当前步骤，记录结果值（rewrite 文本等） */
    public RagTrace endStep(String result) {
        if (currentStep == null) return this;
        int ms = (int) (System.currentTimeMillis() - currentStepStart);
        switch (currentStep) {
            case "rewrite" -> { rewritten = result; rewriteMs = ms; }
            case "embed"   -> embedMs = ms;
            case "vector"  -> vectorMs = ms;
            case "keyword" -> keywordMs = ms;
            case "rrf"     -> rrfMs = ms;
            case "reranker" -> rerankerMs = ms;
        }
        currentStep = null;
        return this;
    }

    /** 记录数量类指标 */
    public RagTrace set(String key, int value) {
        switch (key) {
            case "vectorHits"  -> vectorHits = value;
            case "keywordHits" -> keywordHits = value;
            case "rrfFused"    -> rrfFused = value;
        }
        return this;
    }

    /** 记录 Reranker 精排结果 */
    public RagTrace addRerankHit(String docName, double score) {
        if (rerankTop.size() < 5) {
            rerankTop.add(new RerankerHit(docName, score));
        }
        return this;
    }

    /** 结束追踪，输出结构化 JSON */
    public String finish(int llmTokens, long llmMs) {
        int totalMs = (int) (System.currentTimeMillis() - start);

        Map<String, Object> trace = new LinkedHashMap<>();
        trace.put("query", query);
        if (rewritten != null && !rewritten.equals(query)) {
            trace.put("rewritten", rewritten);
            trace.put("rewriteMs", rewriteMs);
        }
        trace.put("embedMs", embedMs);
        trace.put("vectorHits", vectorHits);
        trace.put("vectorMs", vectorMs);
        trace.put("keywordHits", keywordHits);
        trace.put("keywordMs", keywordMs);
        trace.put("rrfFused", rrfFused);
        trace.put("rrfMs", rrfMs);
        if (!rerankTop.isEmpty()) {
            trace.put("rerankTop", rerankTop);
            trace.put("rerankerMs", rerankerMs);
        }
        trace.put("llmTokens", llmTokens);
        trace.put("llmMs", llmMs);
        trace.put("totalMs", totalMs);

        try {
            return MAPPER.writeValueAsString(trace);
        } catch (JsonProcessingException e) {
            return trace.toString();
        }
    }

    /** 便捷方法：只记录 token 数，LLM 耗时用 total - 其他步 */
    public String finish(int llmTokens) {
        int otherMs = rewriteMs + embedMs + vectorMs + keywordMs + rrfMs + rerankerMs;
        int llmMs = Math.max(0, (int) (System.currentTimeMillis() - start) - otherMs);
        return finish(llmTokens, llmMs);
    }

    public record RerankerHit(String docName, double score) {}
}
