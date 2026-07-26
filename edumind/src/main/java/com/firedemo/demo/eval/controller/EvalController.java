package com.firedemo.demo.eval.controller;

import com.firedemo.demo.eval.model.EvalCase;
import com.firedemo.demo.eval.model.EvalResponse;
import com.firedemo.demo.eval.model.EvalRunRequest;
import com.firedemo.demo.eval.service.EvalService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * RAG 评估 REST API（纯 Java LLM-as-Judge）。
 *
 * <ul>
 *   <li>{@code POST /api/eval/run} — 执行评估</li>
 *   <li>{@code POST /api/eval/dataset/generate} — 从 DB 自动生成评测数据集</li>
 *   <li>{@code GET /api/eval/health} — 健康检查</li>
 * </ul>
 */
@Slf4j
@RestController
@RequestMapping("/api/eval")
public class EvalController {

    private final EvalService evalService;

    public EvalController(EvalService evalService) {
        this.evalService = evalService;
    }

    /**
     * 执行 RAG 评估。数据集为空时自动从 DB 生成。
     *
     * <pre>
     * POST /api/eval/run
     * { "metrics": ["faithfulness", "answer_relevancy"], "topK": 5 }
     * </pre>
     */
    @PostMapping("/run")
    public ResponseEntity<EvalResponse> runEvaluation(@RequestBody EvalRunRequest request) {
        log.info("评估请求: metrics={}, topK={}", request.getMetrics(), request.getTopK());

        EvalResponse response = evalService.runEvaluation(
                request.getMetrics(),
                request.getTopK(),
                request.isEnableReranker(),
                request.isGenerateAnswers());

        if (response.isOk()) {
            return ResponseEntity.ok(response);
        }
        return ResponseEntity.internalServerError().body(response);
    }

    /**
     * 从数据库已索引文档直接生成评测数据集。
     * 不传 docIds 则取全部文档，每个文档默认生成 3 题。
     *
     * <pre>
     * POST /api/eval/dataset/generate
     * { "docIds": [], "questionCount": 5 }
     * </pre>
     */
    @PostMapping("/dataset/generate")
    public ResponseEntity<Map<String, Object>> generateDataset(
            @RequestBody Map<String, Object> body) {

        @SuppressWarnings("unchecked")
        List<String> docIds = (List<String>) body.getOrDefault("docIds", List.of());
        int questionCount = body.containsKey("questionCount")
                ? ((Number) body.get("questionCount")).intValue() : 3;

        List<EvalCase> cases = evalService.generateDatasetFromDb(docIds, questionCount);

        return ResponseEntity.ok(Map.of(
                "status", "ok",
                "count", cases.size(),
                "cases", cases));
    }

    @GetMapping("/health")
    public ResponseEntity<Map<String, String>> health() {
        return ResponseEntity.ok(Map.of(
                "status", "ok",
                "method", "LLM-as-Judge (纯 Java)",
                "metrics", "faithfulness + answer_relevancy"));
    }
}
