package com.firedemo.demo.eval;

import com.firedemo.demo.Service.OpenClawService;
import com.firedemo.demo.common.prompt.PromptLoader;
import com.firedemo.demo.rag.EmbeddingService;
import com.firedemo.demo.rag.RerankerService;
import com.firedemo.demo.rag.RrfFusionService;
import com.firedemo.demo.rag.VectorStoreService;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * RAG 评估测试 — 仅手动运行
 * <p>
 * 检索质量（秒出）：mvn test -Dtest=RagEvaluatorTest
 * 完整评估含 LLM-as-Judge（2-3 分钟）
 */
@Slf4j
@SpringBootTest
class RagEvaluatorTest {

    @Autowired
    private EvalDataset dataset;
    @Autowired
    private EmbeddingService embeddingService;
    @Autowired
    private VectorStoreService vectorStoreService;
    @Autowired
    private RrfFusionService rrfFusionService;
    @Autowired
    private RerankerService rerankerService;
    @Autowired
    private OpenClawService openClawService;
    @Autowired
    private PromptLoader promptLoader;

    @Test
    void testFullEvaluation() {
        if (dataset.getAll().isEmpty()) {
            log.info("评估数据集为空，跳过");
            return;
        }
        RagEvaluator evaluator = new RagEvaluator(
                dataset, embeddingService, vectorStoreService, rrfFusionService,
                rerankerService, openClawService, promptLoader);
        EvalReport report = evaluator.evaluate();
        log.info("\n{}", report.format());
    }
}
