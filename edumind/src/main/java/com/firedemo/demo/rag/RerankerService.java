package com.firedemo.demo.rag;

import ai.djl.Application;
import ai.djl.inference.Predictor;
import ai.djl.repository.zoo.Criteria;
import ai.djl.repository.zoo.ZooModel;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Reranker service - Cross-Encoder fine-ranking with bge-reranker-base.
 */
@Slf4j
@Service
public class RerankerService {

    @Value("${app.ai.reranker.model-dir}")
    private String modelDir;
    private static final String MODEL_NAME = "onnx/model.onnx";

    private ZooModel<QueryDocPair, float[]> model;
    private Predictor<QueryDocPair, float[]> predictor;
    private boolean modelReady = false;

    @PostConstruct
    public void init() {
        Path modelPath = Path.of(modelDir);
        Path onnxPath = modelPath.resolve(MODEL_NAME);
        if (!Files.exists(onnxPath)) {
            log.warn("Reranker ONNX model not found at {}, Reranker disabled", onnxPath);
            return;
        }

        try {
            OnnxRerankerTranslator translator = new OnnxRerankerTranslator(modelPath);

            Criteria<QueryDocPair, float[]> criteria = Criteria.builder()
                    .setTypes(QueryDocPair.class, float[].class)
                    .optModelPath(modelPath)
                    .optModelName(MODEL_NAME)
                    .optEngine("OnnxRuntime")
                    .optApplication(Application.NLP.TEXT_CLASSIFICATION)
                    .optTranslator(translator)
                    .build();

            model = criteria.loadModel();
            predictor = model.newPredictor();
            modelReady = true;

            float[] result = predictor.predict(new QueryDocPair("test", "test doc"));
            log.info("RerankerService initialized, model={}, test score={}", modelPath, result[0]);
        } catch (Exception e) {
            log.error("Failed to load Reranker model: {}", e.getMessage(), e);
        }
    }

    /**
     * Reranker 精排序 —— 按 query-doc 相关度重新打分
     * <p>
     * 使用 parallelStream 并行推理，ONNX Runtime session 支持多线程并行执行。
     * <p>
     * TODO: 改为真正的 stacked-batch 推理（需改造 OnnxRerankerTranslator，
     * 将所有 [query, doc] 对 padded 后拼成 [batch, 2, max_len] 张量一次性推理）
     */
    public List<RrfFusionService.ScoredChunk> rerank(
            String query, List<RrfFusionService.ScoredChunk> candidates, int topK, RagTrace trace) {

        if (candidates == null || candidates.isEmpty()) return List.of();
        if (!modelReady || candidates.size() <= topK) return candidates;

        List<RrfFusionService.ScoredChunk> scored = candidates.stream()
                .filter(sc -> sc.chunk().getContent() != null && !sc.chunk().getContent().isBlank())
                .map(sc -> new RrfFusionService.ScoredChunk(sc.chunk(), computeScore(query, sc.chunk().getContent())))
                .sorted(Comparator.comparingDouble(RrfFusionService.ScoredChunk::score).reversed())
                .toList();

        List<RrfFusionService.ScoredChunk> top = scored.subList(0, Math.min(topK, scored.size()));

        if (trace != null) {
            for (RrfFusionService.ScoredChunk sc : top) {
                String docName = sc.chunk().getDocumentName();
                trace.addRerankHit(docName != null ? docName : "unknown", sc.score());
            }
        }

        return top;
    }

    public boolean isModelReady() { return modelReady; }

    private float computeScore(String query, String document) {
        try {
            float[] result = predictor.predict(new QueryDocPair(query, document));
            return result != null && result.length > 0 ? result[0] : 0.5f;
        } catch (Exception e) {
            log.debug("Reranker inference failed: {}", e.getMessage());
            return 0.5f;
        }
    }
}
