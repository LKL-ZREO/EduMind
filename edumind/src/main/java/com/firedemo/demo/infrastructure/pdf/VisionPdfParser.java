package com.firedemo.demo.infrastructure.pdf;

import com.firedemo.demo.config.properties.VisionPdfProperties;
import com.firedemo.demo.vision.VisualAsset;
import com.firedemo.demo.vision.VisualAssetService;
import com.firedemo.demo.vision.VisionModelClient;
import com.firedemo.demo.vision.VisionTask;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.springframework.stereotype.Component;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicInteger;

@Slf4j
@Component
public class VisionPdfParser {

    private static final int RENDER_DPI = 200;
    private static final int RETRY_MAX = 2;
    private static final long RETRY_DELAY_BASE_MS = 1500L;

    private final VisualAssetService visualAssetService;
    private final VisionModelClient visionModelClient;
    private final VisionPdfProperties properties;
    private final ExecutorService executor;

    public VisionPdfParser(VisualAssetService visualAssetService,
                           VisionModelClient visionModelClient,
                           VisionPdfProperties properties) {
        this.visualAssetService = visualAssetService;
        this.visionModelClient = visionModelClient;
        this.properties = properties;
        this.executor = Executors.newVirtualThreadPerTaskExecutor();
        log.info("VisionPdfParser initialized: concurrency={}, maintainFormat={}, maxPages={}",
                properties.getConcurrency(), properties.isMaintainFormat(), properties.getMaxPdfPages());
    }

    public String parse(Path pdfPath) {
        if (pdfPath == null || !Files.exists(pdfPath)) {
            log.error("PDF file does not exist: {}", pdfPath);
            return "";
        }

        long startedAt = System.currentTimeMillis();
        try (PDDocument document = Loader.loadPDF(pdfPath.toFile())) {
            int totalPages = Math.min(document.getNumberOfPages(), properties.getMaxPdfPages());
            PDFRenderer renderer = new PDFRenderer(document);
            List<PageImage> pages = new ArrayList<>(totalPages);

            for (int index = 0; index < totalPages; index++) {
                BufferedImage image = renderer.renderImageWithDPI(index, RENDER_DPI);
                pages.add(new PageImage(index + 1, encodeToPng(image)));
            }

            List<String> results = properties.isMaintainFormat()
                    ? processSequentialWithContext(pages)
                    : processConcurrent(pages);
            String markdown = String.join("\n\n", results);
            log.info("Vision PDF completed: file={}, pages={}, chars={}, elapsedMs={}",
                    pdfPath.getFileName(), totalPages, markdown.length(),
                    System.currentTimeMillis() - startedAt);
            return markdown;
        } catch (IOException e) {
            log.error("Vision PDF parsing failed: {}", pdfPath, e);
            return "";
        }
    }

    List<String> processConcurrent(List<PageImage> pages) {
        int total = pages.size();
        Semaphore semaphore = new Semaphore(properties.getConcurrency());
        AtomicInteger completed = new AtomicInteger();
        List<CompletableFuture<IndexedResult>> futures = new ArrayList<>();

        for (PageImage page : pages) {
            futures.add(CompletableFuture.supplyAsync(() -> {
                semaphore.acquireUninterruptibly();
                try {
                    String markdown = analyzePage(page, total, null);
                    int done = completed.incrementAndGet();
                    if (done % 5 == 0 || done == total) {
                        log.info("Vision PDF progress: {}/{}", done, total);
                    }
                    return new IndexedResult(page.pageNumber(), markdown);
                } finally {
                    semaphore.release();
                }
            }, executor));
        }

        return futures.stream()
                .map(CompletableFuture::join)
                .filter(Objects::nonNull)
                .sorted(Comparator.comparingInt(IndexedResult::index))
                .map(IndexedResult::markdown)
                .toList();
    }

    List<String> processSequentialWithContext(List<PageImage> pages) {
        List<String> results = new ArrayList<>();
        String previousTail = null;

        for (PageImage page : pages) {
            String markdown = analyzePage(page, pages.size(), previousTail);
            results.add(markdown);
            previousTail = markdown.length() > properties.getContextTailChars()
                    ? markdown.substring(markdown.length() - properties.getContextTailChars())
                    : markdown;
        }
        return results;
    }

    private String analyzePage(PageImage page, int totalPages, String previousTail) {
        VisualAsset asset = visualAssetService.importBytes(page.png(), "image/png");
        String prompt = buildPrompt(page.pageNumber(), totalPages, previousTail);
        Exception lastError = null;

        for (int attempt = 1; attempt <= RETRY_MAX; attempt++) {
            try {
                return visionModelClient.analyze(asset, VisionTask.OCR, prompt).summary();
            } catch (Exception e) {
                lastError = e;
                if (attempt < RETRY_MAX) sleep(attempt * RETRY_DELAY_BASE_MS);
            }
        }

        log.error("Vision PDF page analysis failed: page={}, error={}",
                page.pageNumber(), lastError != null ? lastError.getMessage() : "unknown");
        return "";
    }

    private String buildPrompt(int pageNumber, int totalPages, String previousTail) {
        StringBuilder prompt = new StringBuilder();
        if (previousTail != null && !previousTail.isBlank()) {
            prompt.append("Previous page Markdown tail, provided only for cross-page continuity:\n")
                    .append(previousTail)
                    .append("\n\n");
        }
        prompt.append("Convert this PDF page (page ")
                .append(pageNumber)
                .append(" of ")
                .append(totalPages)
                .append(") into well-structured Markdown.\n")
                .append("""
                        Requirements:
                        1. Preserve heading, paragraph, and list structure.
                        2. Convert tables to complete Markdown tables without dropping rows or columns.
                        3. Use LaTeX for mathematical formulas.
                        4. Use fenced code blocks with language identifiers for code.
                        5. Describe images and charts in HTML comments.
                        6. Return only the converted content, without commentary or a summary.
                        """);
        return prompt.toString();
    }

    byte[] encodeToPng(BufferedImage image) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        ImageIO.write(image, "png", output);
        return output.toByteArray();
    }

    private void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    @PreDestroy
    public void shutdown() {
        executor.close();
    }

    record PageImage(int pageNumber, byte[] png) {
    }

    record IndexedResult(int index, String markdown) {
    }
}
