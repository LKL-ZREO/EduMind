package com.firedemo.demo.eval.controller;

import com.firedemo.demo.eval.model.DatasetGenerationRequest;
import com.firedemo.demo.eval.model.EvalCase;
import com.firedemo.demo.eval.model.EvalResponse;
import com.firedemo.demo.eval.model.EvalRunDetails;
import com.firedemo.demo.eval.model.EvalRunRequest;
import com.firedemo.demo.eval.persistence.EvalRunEntity;
import com.firedemo.demo.eval.service.EvalService;
import jakarta.validation.Valid;
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
 *   <li>{@code GET /api/eval/runs} — 查询历史运行</li>
 *   <li>{@code GET /api/eval/runs/{runId}} — 查询逐条评测证据</li>
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
    public ResponseEntity<EvalResponse> runEvaluation(@Valid @RequestBody EvalRunRequest request) {
        log.info("评估请求: metrics={}, topK={}", request.getMetrics(), request.getTopK());

        EvalResponse response = evalService.runEvaluation(request);

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
            @Valid @RequestBody DatasetGenerationRequest request) {
        List<EvalCase> cases = evalService.generateDatasetFromDb(
                request.getDocIds(), request.getQuestionCount());

        return ResponseEntity.ok(Map.of(
                "status", "ok",
                "count", cases.size(),
                "cases", cases));
    }

    @GetMapping("/runs")
    public List<EvalRunEntity> listRuns(@RequestParam(defaultValue = "20") int limit) {
        return evalService.listRuns(limit);
    }

    @GetMapping("/runs/{runId}")
    public ResponseEntity<EvalRunDetails> getRun(@PathVariable Long runId) {
        EvalRunDetails details = evalService.getRun(runId);
        return details == null ? ResponseEntity.notFound().build() : ResponseEntity.ok(details);
    }

    @GetMapping("/health")
    public ResponseEntity<Map<String, String>> health() {
        return ResponseEntity.ok(Map.of(
                "status", "ok",
                "method", "LLM-as-Judge (纯 Java)",
                "metrics", "retrieval + faithfulness + answer_relevancy"));
    }
}
