package com.firedemo.demo.rag;

import ai.djl.Application;
import ai.djl.inference.Predictor;
import ai.djl.repository.zoo.Criteria;
import ai.djl.repository.zoo.ZooModel;
import ai.djl.training.util.ProgressBar;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

/**
 * 嵌入服务 - 使用 ONNX 真实嵌入模型
 * <p>从 ModelScope / HuggingFace 镜像下载 bge-small-zh-v1.5，
 * 通过 DJL ONNX Runtime 引擎推理，自动回退哈希模式。
 */
@Slf4j
@Service
public class EmbeddingService {

    // paraphrase-multilingual-MiniLM-L12-v2: 384维，50+语言，中文效果良好
    private static final String MODEL_NAME = "BAAI/bge-small-zh-v1.5";
    private static final int EMBEDDING_DIM = 512;

    // BGE 模型 query 侧指令前缀（文档侧不加）
    private static final String BGE_QUERY_PREFIX = "为这个句子生成表示以用于检索相关文章：";

    // 国内镜像（按优先级）
    private static final String[] HF_MIRRORS = {
            "https://hf-mirror.com",
            "https://huggingface.co"
    };

    private ZooModel<String, float[]> model;
    private Predictor<String, float[]> predictor;
    private boolean modelReady = false;

    @PostConstruct
    public void init() {
        Path modelDir = Path.of(System.getProperty("user.home"), ".djl", "models", MODEL_NAME.replace("/", "_"));

        // 1. 检查本地是否已有模型
        if (Files.exists(modelDir.resolve("model.onnx"))) {
            log.info("Found local model at {}", modelDir);
            loadFromLocal(modelDir);
            return;
        }

        // 2. 从镜像下载模型到本地
        for (String mirror : HF_MIRRORS) {
            try {
                log.info("Downloading model from {} ...", mirror);
                if (downloadModel(mirror, modelDir)) {
                    loadFromLocal(modelDir);
                    if (modelReady) {
                        log.info("EmbeddingService initialized with {} (mirror: {})", MODEL_NAME, mirror);
                        return;
                    }
                }
            } catch (Exception e) {
                log.warn("Failed to load from {}: {}", mirror, e.getMessage());
            }
        }

        log.warn("All model sources failed, falling back to hash-based embedding");
    }

    /**
     * 从镜像下载 model.onnx 和 tokenizer.json
     */
    private boolean downloadModel(String mirror, Path targetDir) throws Exception {
        String baseUrl = mirror + "/" + MODEL_NAME + "/resolve/main";

        Files.createDirectories(targetDir);

        // 下载 model.onnx (~120MB)
        Path onnxPath = targetDir.resolve("model.onnx");
        if (!Files.exists(onnxPath)) {
            log.info("Downloading model.onnx ...");
            downloadFile(baseUrl + "/model.onnx", onnxPath);
        }

        // 下载 tokenizer.json
        Path tokPath = targetDir.resolve("tokenizer.json");
        if (!Files.exists(tokPath)) {
            downloadFile(baseUrl + "/tokenizer.json", tokPath);
        }

        // 下载 tokenizer_config.json
        Path tokCfgPath = targetDir.resolve("tokenizer_config.json");
        if (!Files.exists(tokCfgPath)) {
            try {
                downloadFile(baseUrl + "/tokenizer_config.json", tokCfgPath);
            } catch (Exception ignored) {
                // 非必须
            }
        }

        // 下载 special_tokens_map.json
        Path stPath = targetDir.resolve("special_tokens_map.json");
        if (!Files.exists(stPath)) {
            try {
                downloadFile(baseUrl + "/special_tokens_map.json", stPath);
            } catch (Exception ignored) {
                // 非必须
            }
        }

        return Files.exists(onnxPath) && Files.size(onnxPath) > 1024;
    }

    private void downloadFile(String url, Path target) throws Exception {
        java.net.http.HttpClient client = java.net.http.HttpClient.newBuilder()
                .followRedirects(java.net.http.HttpClient.Redirect.NORMAL)
                .connectTimeout(java.time.Duration.ofSeconds(30))
                .build();

        java.net.http.HttpRequest request = java.net.http.HttpRequest.newBuilder()
                .uri(java.net.URI.create(url))
                .timeout(java.time.Duration.ofMinutes(5))
                .GET()
                .build();

        java.net.http.HttpResponse<Path> response = client.send(request,
                java.net.http.HttpResponse.BodyHandlers.ofFile(target));

        if (response.statusCode() != 200) {
            Files.deleteIfExists(target);
            throw new IOException("HTTP " + response.statusCode() + " for " + url);
        }

        log.info("Downloaded {} ({} bytes)", target.getFileName(), Files.size(target));
    }

    /**
     * 从本地目录加载已下载的模型
     */
    private void loadFromLocal(Path modelDir) {
        try {
            Criteria<String, float[]> criteria = Criteria.builder()
                    .setTypes(String.class, float[].class)
                    .optModelPath(modelDir)
                    .optModelName("model.onnx")
                    .optEngine("OnnxRuntime")
                    .optApplication(Application.NLP.TEXT_EMBEDDING)
                    .optTranslator(new OnnxEmbeddingTranslator())
                    .optProgress(new ProgressBar())
                    .build();

            model = criteria.loadModel();
            predictor = model.newPredictor();
            modelReady = true;
        } catch (Exception e) {
            log.error("Failed to load local model: {}", e.getMessage());
        }
    }

    // ==================== 公共 API ====================

    /**
     * Query 嵌入 —— 加 BGE 指令前缀，用于检索时
     */
    public float[] embedQuery(String query) {
        if (!modelReady) {
            return hashEmbed(BGE_QUERY_PREFIX + query);
        }
        try {
            return predictor.predict(BGE_QUERY_PREFIX + query);
        } catch (Exception e) {
            log.debug("ONNX inference failed for query, fallback: {}", e.getMessage());
            return hashEmbed(query);
        }
    }

    /**
     * 文档嵌入 —— 不加前缀，用于文档入库时
     */
    public float[] embedDocument(String text) {
        if (!modelReady) {
            return hashEmbed(text);
        }
        try {
            return predictor.predict(text);
        } catch (Exception e) {
            log.debug("ONNX inference failed for doc, fallback: {}", e.getMessage());
            return hashEmbed(text);
        }
    }

    /**
     * 通用嵌入（兼容旧调用，等同于 embedDocument）
     */
    public float[] embed(String text) {
        return embedDocument(text);
    }

    /**
     * 批量嵌入
     */
    public List<float[]> embedBatch(List<String> texts) {
        List<float[]> results = new ArrayList<>();
        for (String text : texts) {
            results.add(embed(text));
        }
        return results;
    }

    /**
     * 模型是否就绪
     */
    public boolean isModelReady() {
        return modelReady;
    }

    // ==================== 回退：哈希嵌入 ====================

    /**
     * 简化版嵌入 - 基于词频的稀疏向量（备用）
     */
    private float[] hashEmbed(String text) {
        try {
            Map<String, Integer> wordFreq = extractKeywords(text);
            float[] vector = new float[EMBEDDING_DIM];

            for (Map.Entry<String, Integer> entry : wordFreq.entrySet()) {
                int idx = Math.abs(entry.getKey().hashCode()) % EMBEDDING_DIM;
                vector[idx] += entry.getValue();
            }

            return normalize(vector);
        } catch (Exception e) {
            return new float[EMBEDDING_DIM];
        }
    }

    private Map<String, Integer> extractKeywords(String text) {
        Map<String, Integer> freq = new HashMap<>();
        String[] tokens = text.toLowerCase()
                .replaceAll("[^\\u4e00-\\u9fa5a-z0-9]", " ")
                .split("\\s+");

        for (String token : tokens) {
            if (token.length() >= 2) freq.merge(token, 1, Integer::sum);
        }

        String chinese = text.replaceAll("[^\\u4e00-\\u9fa5]", "");
        for (int i = 0; i < chinese.length() - 1; i++) {
            freq.merge(chinese.substring(i, i + 2), 1, Integer::sum);
        }

        return freq;
    }

    private float[] normalize(float[] vector) {
        double norm = 0;
        for (float v : vector) norm += v * v;
        norm = Math.sqrt(norm);
        if (norm < 1e-10) return vector;

        float[] normalized = new float[vector.length];
        for (int i = 0; i < vector.length; i++) {
            normalized[i] = (float) (vector[i] / norm);
        }
        return normalized;
    }
}
