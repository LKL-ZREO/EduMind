package com.firedemo.demo.rag;

import com.firedemo.demo.eval.model.EvalResponse;
import com.firedemo.demo.eval.model.EvalRunRequest;
import com.firedemo.demo.eval.service.EvalService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * RAG 检索 + 生成质量评测。
 *
 * <p>统一运行检索与生成评测，保存配置和逐条证据，并强制执行质量门禁。
 *
 * <p>前提：本地 PostgreSQL 中已索引文档 + rag-eval-dataset.json 有数据。</p>
 *
 * <p>运行：
 * <pre>
 *   ./mvnw test -Dtest="RagEvalRunner" -DEVALUATION_ENABLED=true -Dspring.profiles.active=local
 * </pre></p>
 */
@SpringBootTest
@ActiveProfiles("local")
@EnabledIfEnvironmentVariable(named = "EVALUATION_ENABLED", matches = "true")
@DisplayName("RAG 质量评测（检索 + LLM-as-Judge）")
class RagEvalRunner {

    @Autowired
    private EvalService evalService;

    @Test
    @DisplayName("真实管线达到全部 RAG 质量门禁")
    void evaluateRealPipeline() {
        EvalRunRequest request = new EvalRunRequest();
        request.setMetrics(List.of("keyword_recall", "content_coverage", "hit_rate", "mrr",
                "ndcg", "faithfulness", "answer_relevancy"));
        request.setTopK(5);
        request.setEnableReranker(true);
        request.setGenerateAnswers(true);

        EvalResponse response = evalService.runEvaluation(request);

        assertThat(response.isOk()).as("evaluation error: %s", response.getError()).isTrue();
        assertThat(response.isQualityGatePassed())
                .as("RAG 质量门禁失败: %s; scores=%s",
                        response.getQualityGateFailures(), response.getSummary())
                .isTrue();
    }
}
