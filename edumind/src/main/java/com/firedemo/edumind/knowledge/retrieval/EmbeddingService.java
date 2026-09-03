package com.firedemo.edumind.knowledge.retrieval;

import ai.djl.Application;
import ai.djl.inference.Predictor;
import ai.djl.repository.zoo.Criteria;
import ai.djl.repository.zoo.ZooModel;
import ai.djl.training.util.ProgressBar;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.util.List;

/**
 * BGE ONNX embedding service used by document indexing and semantic retrieval.
 * The model is cached locally and startup remains available if every download source fails.
 */
@Slf4j
@Service
public class EmbeddingService {

    static final int EMBEDDING_DIMENSION = 512;
    private static final long MIN_ONNX_BYTES = 10L * 1024 * 1024;
    private static final long MIN_TOKENIZER_BYTES = 1024;
    private static final String BGE_QUERY_PREFIX = "为这个句子生成表示以用于检索相关文章：";

    private static final HttpClient HTTP_CLIENT = HttpClient.newBuilder()
            .followRedirects(HttpClient.Redirect.NORMAL)
            .connectTimeout(Duration.ofSeconds(30))
            .build();

    @Value("${app.ai.embedding.model-dir:${user.home}/.djl/models/Xenova_bge-small-zh-v1.5}")
    private String configuredModelDir = Path.of(System.getProperty("user.home"), ".djl", "models",
            "Xenova_bge-small-zh-v1.5").toString();

    @Value("${app.ai.embedding.repository:Xenova/bge-small-zh-v1.5}")
    private String repository = "Xenova/bge-small-zh-v1.5";

    @Value("${app.ai.embedding.revision:75c43b069aac4d136ba6bc1122f995fedcfd2781}")
    private String revision = "75c43b069aac4d136ba6bc1122f995fedcfd2781";

    @Value("${app.ai.embedding.onnx-path:onnx/model.onnx}")
    private String onnxRemotePath = "onnx/model.onnx";

    @Value("${app.ai.embedding.base-urls:https://hf-mirror.com,https://huggingface.co}")
    private String configuredBaseUrls = "https://hf-mirror.com,https://huggingface.co";

    private ZooModel<String, float[]> model;
    private Predictor<String, float[]> predictor;
    private volatile boolean modelReady;

    @PostConstruct
    public void init() {
        Path modelDir = Path.of(configuredModelDir).toAbsolutePath().normalize();
        if (hasCompleteModel(modelDir)) {
            log.info("Found local embedding model at {}", modelDir);
            if (loadFromLocal(modelDir)) {
                return;
            }
            log.warn("Local embedding model is invalid; download will be retried");
            deleteCachedModelFiles(modelDir);
        }

        for (String baseUrl : configuredBaseUrls.split(",")) {
            String source = baseUrl.trim();
            if (source.isEmpty()) {
                continue;
            }
            try {
                log.info("Downloading embedding model {} from {}", repository, source);
                downloadModel(source, modelDir);
                if (loadFromLocal(modelDir)) {
                    log.info("Embedding model initialized from {}", source);
                    return;
                }
                deleteCachedModelFiles(modelDir);
            } catch (IOException | RuntimeException exception) {
                log.warn("Failed to load embedding model from {}: {}", source, exception.getMessage());
            }
        }

        log.warn("All embedding model sources failed; semantic retrieval is unavailable");
    }

    private void downloadModel(String baseUrl, Path targetDir) throws IOException {
        Files.createDirectories(targetDir);
        for (ModelFile file : modelFiles(onnxRemotePath)) {
            Path target = targetDir.resolve(file.localName());
            if (isValidFile(target, file.minimumBytes())) {
                continue;
            }
            Files.deleteIfExists(target);
            downloadFile(modelFileUri(baseUrl, repository, revision, file.remotePath()), target,
                    file.minimumBytes());
        }
    }

    private void downloadFile(URI uri, Path target, long minimumBytes) throws IOException {
        Path partial = target.resolveSibling(target.getFileName() + ".part");
        Files.deleteIfExists(partial);
        HttpRequest request = HttpRequest.newBuilder()
                .uri(uri)
                .timeout(Duration.ofMinutes(10))
                .GET()
                .build();

        try {
            HttpResponse<Path> response = HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofFile(partial));
            if (response.statusCode() != 200) {
                throw new IOException("HTTP " + response.statusCode() + " for " + uri);
            }
            long size = Files.size(partial);
            if (size < minimumBytes) {
                throw new IOException("Downloaded file is too small (" + size + " bytes): " + uri);
            }
            moveAtomically(partial, target);
            log.info("Downloaded {} ({} bytes)", target.getFileName(), size);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IOException("Download interrupted: " + uri, exception);
        } finally {
            Files.deleteIfExists(partial);
        }
    }

    private boolean loadFromLocal(Path modelDir) {
        closeModel();
        try {
            Criteria<String, float[]> criteria = Criteria.builder()
                    .setTypes(String.class, float[].class)
                    .optModelPath(modelDir)
                    .optModelName("model.onnx")
                    .optEngine("OnnxRuntime")
                    .optApplication(Application.NLP.TEXT_EMBEDDING)
                    .optTranslator(new OnnxEmbeddingTranslator(modelDir))
                    .optProgress(new ProgressBar())
                    .build();

            model = criteria.loadModel();
            predictor = model.newPredictor();
            float[] probe = predictor.predict("语义检索模型启动检查");
            if (probe.length != EMBEDDING_DIMENSION) {
                throw new IllegalStateException("Expected " + EMBEDDING_DIMENSION
                        + " embedding dimensions but model returned " + probe.length);
            }
            modelReady = true;
            log.info("EmbeddingService ready: repository={}, dimension={}, directory={}",
                    repository, EMBEDDING_DIMENSION, modelDir);
            return true;
        } catch (Exception exception) {
            log.error("Failed to load local embedding model from {}: {}", modelDir, exception.getMessage());
            closeModel();
            return false;
        }
    }

    public float[] embedQuery(String query) {
        return predict(BGE_QUERY_PREFIX + query);
    }

    @io.micrometer.core.annotation.Timed(value = "embedding.document", histogram = true)
    public float[] embedDocument(String text) {
        return predict(text);
    }

    private synchronized float[] predict(String text) {
        if (!modelReady || predictor == null) {
            throw new IllegalStateException("Embedding model not ready");
        }
        try {
            return predictor.predict(text);
        } catch (ai.djl.translate.TranslateException exception) {
            throw new RuntimeException("ONNX embedding inference failed", exception);
        }
    }

    @PreDestroy
    void closeModel() {
        modelReady = false;
        if (predictor != null) {
            predictor.close();
            predictor = null;
        }
        if (model != null) {
            model.close();
            model = null;
        }
    }

    static URI modelFileUri(String baseUrl, String repository, String revision, String remotePath) {
        String normalizedBase = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        return URI.create(normalizedBase + "/" + repository + "/resolve/" + revision + "/" + remotePath);
    }

    static List<ModelFile> modelFiles(String onnxPath) {
        return List.of(
                new ModelFile(onnxPath, "model.onnx", MIN_ONNX_BYTES),
                new ModelFile("tokenizer.json", "tokenizer.json", MIN_TOKENIZER_BYTES));
    }

    private static boolean hasCompleteModel(Path modelDir) {
        return modelFiles("model.onnx").stream()
                .allMatch(file -> isValidFile(modelDir.resolve(file.localName()), file.minimumBytes()));
    }

    private static boolean isValidFile(Path path, long minimumBytes) {
        try {
            return Files.isRegularFile(path) && Files.size(path) >= minimumBytes;
        } catch (IOException exception) {
            return false;
        }
    }

    private static void deleteCachedModelFiles(Path modelDir) {
        for (ModelFile file : modelFiles("model.onnx")) {
            try {
                Files.deleteIfExists(modelDir.resolve(file.localName()));
            } catch (IOException exception) {
                log.warn("Failed to remove invalid cached model file {}: {}", file.localName(),
                        exception.getMessage());
            }
        }
    }

    private static void moveAtomically(Path source, Path target) throws IOException {
        try {
            Files.move(source, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (AtomicMoveNotSupportedException exception) {
            Files.move(source, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    record ModelFile(String remotePath, String localName, long minimumBytes) {
    }
}
