package com.firedemo.demo.infrastructure.pdf;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import jakarta.annotation.PostConstruct;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.concurrent.TimeUnit;

/**
 * MinerU PDF 解析客户端，支持两种运行模式：
 *
 * <pre>
 *   CLI 模式（开发推荐）：mineru 通过 pip 安装在本地
 *     配置：mineru.mode=cli
 *     前提：pip install "mineru[core]"
 *
 *   HTTP 模式（生产推荐）：MinerU 作为 Docker 边车服务运行
 *     配置：mineru.mode=http, mineru.api.url=http://localhost:18790
 * </pre>
 *
 * 仅当 mineru.enabled=true 时启用。CLI 模式下首次调用会自动探测 mineru 命令是否可用。
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "mineru.enabled", havingValue = "true")
public class MineruClient {

    @Value("${mineru.mode:auto}")
    private String mode;

    @Value("${mineru.api.url:http://localhost:18790}")
    private String apiUrl;

    @Value("${mineru.cli.timeout-minutes:10}")
    private int cliTimeoutMinutes;

    private RestClient restClient;
    private volatile String resolvedMode;  // "cli" | "http" | null

    @PostConstruct
    void init() {
        if (!"cli".equals(mode)) {
            this.restClient = RestClient.builder()
                    .baseUrl(apiUrl)
                    .build();
        }
        log.info("MineruClient 初始化: mode={}, apiUrl={}", mode, apiUrl);
    }

    // ======================== 公开 API ========================

    /**
     * 解析 PDF，自动选择 CLI 或 HTTP 后端。
     */
    public String parse(String filePath) {
        String m = resolveMode();
        if ("cli".equals(m)) {
            return parseViaCli(filePath);
        }
        return parseViaHttp(filePath);
    }

    public boolean isHealthy() {
        try {
            String m = resolveMode();
            if ("cli".equals(m)) {
                return probeCli();
            }
            var response = restClient.get()
                    .uri("/health")
                    .retrieve()
                    .toEntity(String.class);
            return response.getStatusCode().is2xxSuccessful();
        } catch (Exception e) {
            return false;
        }
    }

    // ======================== 模式自动探测 ========================

    private String resolveMode() {
        if (resolvedMode != null) return resolvedMode;

        if ("cli".equals(mode)) {
            resolvedMode = "cli";
            return "cli";
        }
        if ("http".equals(mode)) {
            resolvedMode = "http";
            return "http";
        }

        // auto: 先探测 HTTP，再探测 CLI
        if (restClient != null && probeHttp()) {
            resolvedMode = "http";
            log.info("MinerU 模式自动选择: HTTP ({} 可达)", apiUrl);
            return "http";
        }
        if (probeCli()) {
            resolvedMode = "cli";
            log.info("MinerU 模式自动选择: CLI (mineru 命令可用)");
            return "cli";
        }

        throw new MineruException(
                "MinerU 不可用: HTTP(" + apiUrl + ") 不可达，且 mineru CLI 命令也找不到。" +
                "请执行 pip install 'mineru[core]' 或启动 Docker mineru 服务");
    }

    private boolean probeHttp() {
        try {
            var r = restClient.get().uri("/health").retrieve().toEntity(String.class);
            return r.getStatusCode().is2xxSuccessful();
        } catch (Exception e) {
            return false;
        }
    }

    private boolean probeCli() {
        try {
            Process p = new ProcessBuilder("mineru", "--version")
                    .redirectErrorStream(true)
                    .start();
            boolean ok = p.waitFor(30, TimeUnit.SECONDS);
            return ok && p.exitValue() == 0;
        } catch (Exception e) {
            return false;
        }
    }

    // ======================== CLI 模式 ========================

    String parseViaCli(String filePath) throws MineruException {
        Path pdfPath = Path.of(filePath);
        if (!Files.exists(pdfPath)) {
            throw new MineruException("文件不存在: " + filePath);
        }

        Path outputDir = null;
        try {
            outputDir = Files.createTempDirectory("mineru_cli_");
            log.info("MinerU CLI 开始解析: {} → {}", filePath, outputDir);

            ProcessBuilder pb = new ProcessBuilder(
                    "mineru",
                    "-p", filePath,
                    "-o", outputDir.toString(),
                    "-m", "auto"
            );
            pb.redirectErrorStream(true);

            Process process = pb.start();
            // 后台线程读 stdout，避免主线程被 readAllBytes 永久阻塞导致超时无效
            java.util.concurrent.CompletableFuture<String> stdoutFuture =
                    java.util.concurrent.CompletableFuture.supplyAsync(() -> {
                        try {
                            return new String(process.getInputStream().readAllBytes());
                        } catch (java.io.IOException e) {
                            return "";
                        }
                    });

            boolean finished = process.waitFor(cliTimeoutMinutes, TimeUnit.MINUTES);
            if (!finished) {
                process.destroyForcibly();
                throw new MineruException("MinerU CLI 超时 (" + cliTimeoutMinutes + " 分钟)，文件可能过大: " + filePath);
            }

            if (process.exitValue() != 0) {
                String stdout = stdoutFuture.get();
                String tail = stdout.length() > 1000
                        ? stdout.substring(stdout.length() - 1000) : stdout;
                throw new MineruException("MinerU CLI 退出码=" + process.exitValue()
                        + ", 输出:\n" + tail);
            }

            // 找到输出的 .md 文件
            Path mdFile = findMarkdownFile(outputDir);
            if (mdFile == null) {
                throw new MineruException("MinerU CLI 未生成 .md 文件: " + outputDir);
            }

            String markdown = Files.readString(mdFile);
            log.info("MinerU CLI 解析成功: {} → {} 字符", filePath, markdown.length());
            return markdown;

        } catch (IOException e) {
            throw new MineruException("MinerU CLI IO 异常: " + e.getMessage(), e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new MineruException("MinerU CLI 被中断", e);
        } catch (java.util.concurrent.ExecutionException e) {
            throw new MineruException("MinerU CLI 执行异常: " + e.getMessage(), e);
        } finally {
            if (outputDir != null) {
                deleteRecursively(outputDir);
            }
        }
    }

    private Path findMarkdownFile(Path outputDir) throws IOException {
        // MinerU 输出: outputDir/<basename>/<basename>.md
        try (var stream = Files.walk(outputDir, 3)) {
            return stream
                    .filter(p -> p.toString().endsWith(".md"))
                    .findFirst()
                    .orElse(null);
        }
    }

    private void deleteRecursively(Path dir) {
        try {
            if (Files.exists(dir)) {
                try (var stream = Files.walk(dir)) {
                    stream.sorted(java.util.Comparator.reverseOrder())
                            .forEach(p -> {
                                try { Files.deleteIfExists(p); }
                                catch (IOException ignored) { }
                            });
                }
            }
        } catch (IOException ignored) { }
    }

    // ======================== HTTP 模式 ========================

    String parseViaHttp(String filePath) throws MineruException {
        int maxRetries = 3;
        var request = new ParseRequest(filePath);

        for (int attempt = 1; attempt <= maxRetries; attempt++) {
            try {
                var response = restClient.post()
                        .uri("/parse")
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(request)
                        .retrieve()
                        .body(ParseResponse.class);

                if (response == null) {
                    throw new MineruException("MinerU 返回空响应");
                }
                if (!"ok".equals(response.status())) {
                    throw new MineruException("MinerU 解析失败: " + response.error());
                }

                log.info("MinerU HTTP 解析成功: {} → {} 字符, {} 页 (attempt={})",
                        filePath, response.charCount(), response.pageCount(), attempt);
                return response.markdown();

            } catch (MineruException e) {
                if (attempt == maxRetries) throw e;
                log.warn("MinerU HTTP {}/{} 失败: {}, 等待重试...", attempt, maxRetries, e.getMessage());
                sleep(attempt * 2000L);
            } catch (Exception e) {
                if (attempt == maxRetries) {
                    throw new MineruException("MinerU HTTP 不可达 (已重试" + maxRetries + "次): " + e.getMessage(), e);
                }
                log.warn("MinerU HTTP {}/{} 通信异常: {}, 等待重试...", attempt, maxRetries, e.getMessage());
                sleep(attempt * 2000L);
            }
        }
        throw new MineruException("MinerU HTTP 解析失败: 不可达");
    }

    private void sleep(long millis) {
        try { Thread.sleep(millis); }
        catch (InterruptedException ie) { Thread.currentThread().interrupt(); }
    }

    // ========== DTO ==========

    record ParseRequest(String filePath) {}
    record ParseResponse(String status, String markdown, int charCount,
                         int pageCount, String error) {}
}
