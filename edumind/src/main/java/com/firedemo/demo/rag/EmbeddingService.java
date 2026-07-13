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
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Collections;
import java.util.List;

/**
 * 嵌入服务 - 使用 ONNX 真实嵌入模型
 * <p>从 ModelScope / HuggingFace 镜像下载 bge-small-zh-v1.5，
 * 通过 DJL ONNX Runtime 引擎推理，模型未就绪时抛出异常。
 */
@Slf4j
@Service
public class EmbeddingService {

    // paraphrase-multilingual-MiniLM-L12-v2: 384维，50+语言，中文效果良好
    private static final String MODEL_NAME = "BAAI/bge-small-zh-v1.5";

    private static final HttpClient HTTP_CLIENT = HttpClient.newBuilder()
            .followRedirects(HttpClient.Redirect.NORMAL)
            .connectTimeout(Duration.ofSeconds(30))
            .build();

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
            } catch (IOException | RuntimeException e) {
                log.warn("Failed to load from {}: {}", mirror, e.getMessage());
            }
        }

        log.warn("All model sources failed, embedding service unavailable");
    }

    /**
     * 从镜像下载 model.onnx 和 tokenizer.json
     */
    private boolean downloadModel(String mirror, Path targetDir) throws IOException {
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
            } catch (IOException e) {
                log.debug("下载 tokenizer_config.json 失败（非必须）: {}", e.getMessage());
            }
        }

        // 下载 special_tokens_map.json
        Path stPath = targetDir.resolve("special_tokens_map.json");
        if (!Files.exists(stPath)) {
            try {
                downloadFile(baseUrl + "/special_tokens_map.json", stPath);
            } catch (IOException e) {
                log.debug("下载 special_tokens_map.json 失败（非必须）: {}", e.getMessage());
            }
        }

        return Files.exists(onnxPath) && Files.size(onnxPath) > 1024;
    }

    private void downloadFile(String url, Path target) throws IOException {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofMinutes(5))
                .GET()
                .build();

        HttpResponse<Path> response;
        try {
            response = HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofFile(target));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Download interrupted: " + url, e);
        }

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
        } catch (IOException | ai.djl.ModelException | RuntimeException e) {
            log.error("Failed to load local model: {}", e.getMessage());
        }
    }

    // ==================== 公共 API ====================

    /**
     * Query 嵌入 —— 加 BGE 指令前缀，用于检索时。
     * 模型未就绪或推理失败时直接抛异常，不再静默回退 hashEmbed（假向量会污染检索结果）。
     */
    public float[] embedQuery(String query) {
        if (!modelReady) {
            throw new IllegalStateException("Embedding model not ready");
        }
        try {
            return predictor.predict(BGE_QUERY_PREFIX + query);
        } catch (ai.djl.translate.TranslateException e) {
            throw new RuntimeException("ONNX embedding inference failed", e);
        }
    }

    /**
     * 文档嵌入 —— 不加前缀，用于文档入库时。
     * 模型未就绪或推理失败时直接抛异常，调用方自行决定是否重试或跳过。
     */
    @io.micrometer.core.annotation.Timed(value = "embedding.document", histogram = true)
    public float[] embedDocument(String text) {
        if (!modelReady) {
            throw new IllegalStateException("Embedding model not ready");
        }
        try {
            return predictor.predict(text);
        } catch (ai.djl.translate.TranslateException e) {
            throw new RuntimeException("ONNX embedding inference failed", e);
        }
    }

    /**
     * 通用嵌入（兼容旧调用，等同于 embedDocument）
     */
    public float[] embed(String text) {
        return embedDocument(text);
    }

    /**
     * 批量嵌入 —— 顺序单条推理。
     * 当前 Predictor 不保证线程安全，使用 stream（非 parallelStream）。
     * <p>
     * TODO: 改为真正的 stacked-batch 推理 + Predictor 池（需改造 OnnxEmbeddingTranslator
     * 为 BatchTranslator，将所有输入 padded 后拼成 [batch, max_len] 张量一次性推理）
     */
    @io.micrometer.core.annotation.Timed(value = "embedding.batch", histogram = true)
    public List<float[]> embedBatch(List<String> texts) {
        if (texts == null || texts.isEmpty()) {
            return Collections.emptyList();
        }
        return texts.stream()
                .map(this::embed)
                .toList();
    }

}
