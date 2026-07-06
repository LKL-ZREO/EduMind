package com.firedemo.demo.common;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;

/**
 * 轻量级 OCR 兜底服务 — 对 Tika 无法解析的扫描件 PDF 调用 Tesseract CLI。
 *
 * <p>前提：安装 Tesseract OCR 并下载中文语言包
 * <pre>
 *   Windows: https://github.com/UB-Mannheim/tesseract/wiki
 *            安装时勾选 Chinese Simplified
 *   Linux:   apt install tesseract-ocr tesseract-ocr-chi-sim
 *   macOS:   brew install tesseract tesseract-lang
 * </pre>
 *
 * <p>内存占用 &lt; 500MB，模型 &lt; 100MB，对比 MinerU (16GB+) 是零头。
 */
@Slf4j
@Service
public class OcrService {

    @Value("${ocr.tesseract.timeout-minutes:5}")
    private int timeoutMinutes;

    @Value("${ocr.tesseract.languages:chi_sim+eng}")
    private String languages;

    /**
     * 用 Tesseract 对 PDF 做 OCR，返回纯文本。
     * 扫描件 PDF 的每一页会被渲染为图像然后识别。
     *
     * @param pdfPath PDF 文件绝对路径
     * @return 识别出的文本，失败返回 null
     */
    public String ocr(Path pdfPath) {
        if (!isTesseractAvailable()) {
            log.warn("Tesseract 未安装或不在 PATH 中，跳过 OCR");
            return null;
        }

        Path outputDir = null;
        try {
            // Tesseract 输出目录（它会生成 output.txt）
            outputDir = Files.createTempDirectory("tess_");
            String outputBase = outputDir.resolve("output").toString();

            log.info("Tesseract OCR 开始: {} (语言: {}, 超时: {}min)",
                    pdfPath.getFileName(), languages, timeoutMinutes);

            ProcessBuilder pb = new ProcessBuilder(
                    "tesseract",
                    pdfPath.toString(),        // 输入 PDF
                    outputBase,                // 输出前缀（生成 output.txt）
                    "-l", languages,           // 语言
                    "--psm", "3"               // 自动页面分割
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

            boolean finished = process.waitFor(timeoutMinutes, TimeUnit.MINUTES);
            if (!finished) {
                process.destroyForcibly();
                log.warn("Tesseract OCR 超时 ({}min): {}", timeoutMinutes, pdfPath.getFileName());
                return null;
            }

            if (process.exitValue() != 0) {
                String stdOutput = stdoutFuture.get();
                log.warn("Tesseract 退出码={}: {}", process.exitValue(),
                        stdOutput.length() > 500 ? stdOutput.substring(0, 500) : stdOutput);
                return null;
            }

            // 读取输出
            Path txtFile = Path.of(outputBase + ".txt");
            if (!Files.exists(txtFile)) {
                log.warn("Tesseract 未生成输出文件: {}", txtFile);
                return null;
            }

            String text = Files.readString(txtFile);
            log.info("Tesseract OCR 完成: {} → {} 字符", pdfPath.getFileName(), text.length());
            return text;

        } catch (IOException e) {
            log.error("Tesseract OCR IO 异常: {}", e.getMessage());
            return null;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("Tesseract OCR 被中断");
            return null;
        } catch (java.util.concurrent.ExecutionException e) {
            log.warn("Tesseract OCR 执行异常: {}", e.getMessage());
            return null;
        } finally {
            if (outputDir != null) {
                try (var stream = Files.walk(outputDir)) {
                    stream.sorted(java.util.Comparator.reverseOrder())
                            .forEach(p -> {
                                try { Files.deleteIfExists(p); }
                                catch (IOException ignored) { }
                            });
                } catch (IOException ignored) { }
            }
        }
    }

    private volatile Boolean available = null;

    /**
     * 探测 tesseract 命令是否可用（缓存结果）
     */
    public boolean isTesseractAvailable() {
        if (available != null) return available;
        try {
            Process p = new ProcessBuilder("tesseract", "--version")
                    .redirectErrorStream(true)
                    .start();
            boolean ok = p.waitFor(10, TimeUnit.SECONDS) && p.exitValue() == 0;
            available = ok;
            if (ok) {
                log.info("Tesseract OCR 可用");
            }
            return ok;
        } catch (Exception e) {
            available = false;
            return false;
        }
    }
}
