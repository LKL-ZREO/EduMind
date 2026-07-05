package com.firedemo.demo.common;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import jakarta.annotation.PreDestroy;
import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 多模态 LLM PDF 解析器 — 对标 gptpdf / Zerox 范式。
 *
 * <h3>直连视觉模型 API（绕过 OpenClaw）</h3>
 * <p>
 * OpenClaw 无法在文本模型和视觉模型间自动路由，所以这里直连 Kimi API：
 * <pre>
 *   PDF 页面 → PNG base64 → POST https://api.moonshot.cn/v1/chat/completions
 *                           → model: kimi-k2.5
 *                           → Markdown
 *   (中国站 Key 用 .cn, 海外站 Key 用 .ai, 通过 VISION_PDF_API_URL 环境变量覆盖)
 * </pre>
 *
 * <h3>两种处理模式</h3>
 * <pre>
 *   并发模式（默认，maintainFormat=false）：
 *     N 页并行发给 Kimi，Semaphore(concurrency) 限流
 *     10 页 PDF ~3s（vs 串行 ~30s）
 *
 *   跨页上下文模式（maintainFormat=true）：
 *     逐页串行，每页传入前一页 Markdown 末尾 N 字符
 *     牺牲速度换跨页表格/公式连贯性
 * </pre>
 */
@Slf4j
@Component
public class VisionPdfParser {

    private final RestClient restClient;
    private final ObjectMapper objectMapper;

    private final String apiBaseUrl;
    private final String apiKey;
    private final String model;
    private final int concurrency;
    private final boolean maintainFormat;
    private final int maxPdfPages;
    private final int contextTailChars;

    private static final int RENDER_DPI = 200;
    private static final int RETRY_MAX = 2;

    private final ExecutorService executor;

    public VisionPdfParser(ObjectMapper objectMapper,
                           @Value("${vision-pdf.api-base-url:https://api.moonshot.ai/v1}") String apiBaseUrl,
                           @Value("${vision-pdf.api-key:}") String apiKey,
                           @Value("${vision-pdf.model:kimi-k2.5}") String model,
                           @Value("${vision-pdf.concurrency:10}") int concurrency,
                           @Value("${vision-pdf.maintain-format:false}") boolean maintainFormat,
                           @Value("${vision-pdf.max-pdf-pages:50}") int maxPdfPages,
                           @Value("${vision-pdf.context-tail-chars:500}") int contextTailChars) {
        this.objectMapper = objectMapper;
        this.apiBaseUrl = apiBaseUrl;
        this.apiKey = apiKey;
        this.model = model;
        this.concurrency = concurrency;
        this.maintainFormat = maintainFormat;
        this.maxPdfPages = maxPdfPages;
        this.contextTailChars = contextTailChars;

        this.restClient = RestClient.builder()
                .baseUrl(apiBaseUrl)
                .defaultHeader("Authorization", "Bearer " + apiKey)
                .defaultHeader("Content-Type", MediaType.APPLICATION_JSON_VALUE)
                .build();

        this.executor = Executors.newVirtualThreadPerTaskExecutor();

        log.info("VisionPdfParser 初始化: api={}, model={}, concurrency={}, maintainFormat={}, maxPages={}",
                apiBaseUrl, model, concurrency, maintainFormat, maxPdfPages);
    }

    // ======================== 公开 API ========================

    /**
     * 解析 PDF 文件，返回 Markdown。
     */
    public String parse(Path pdfPath) {
        if (!Files.exists(pdfPath)) {
            log.error("PDF 文件不存在: {}", pdfPath);
            return "";
        }

        long t0 = System.currentTimeMillis();

        try (PDDocument doc = Loader.loadPDF(pdfPath.toFile())) {
            int totalPages = doc.getNumberOfPages();
            log.info("Vision PDF 开始: {} ({} 页, concurrency={}, maintainFormat={})",
                    pdfPath.getFileName(), totalPages, concurrency, maintainFormat);

            if (totalPages > maxPdfPages) {
                log.warn("PDF 页数({})超过上限({})，截断处理", totalPages, maxPdfPages);
                totalPages = maxPdfPages;
            }

            PDFRenderer renderer = new PDFRenderer(doc);

            // 1) 预渲染所有页面为 base64
            List<PageImage> pages = new ArrayList<>(totalPages);
            for (int i = 0; i < totalPages; i++) {
                BufferedImage image = renderer.renderImageWithDPI(i, RENDER_DPI);
                String base64 = encodeToBase64Png(image);
                pages.add(new PageImage(i + 1, base64));
            }

            // 2) 按模式处理
            List<String> results;
            if (maintainFormat) {
                results = processSequentialWithContext(pages);
            } else {
                results = processConcurrent(pages);
            }

            String result = String.join("\n\n", results);
            long elapsed = System.currentTimeMillis() - t0;
            log.info("Vision PDF 完成: {} → {} 字符, {} 页, 耗时 {}ms",
                    pdfPath.getFileName(), result.length(), totalPages, elapsed);
            return result;

        } catch (IOException e) {
            log.error("Vision PDF 解析失败: {}", e.getMessage(), e);
            return "";
        }
    }

    // ======================== 并发模式 ========================

    List<String> processConcurrent(List<PageImage> pages) {
        int total = pages.size();
        Semaphore semaphore = new Semaphore(concurrency);
        List<CompletableFuture<IndexedResult>> futures = new ArrayList<>();
        AtomicInteger completed = new AtomicInteger(0);

        for (PageImage page : pages) {
            CompletableFuture<IndexedResult> f = CompletableFuture.supplyAsync(() -> {
                semaphore.acquireUninterruptibly();
                try {
                    String md = callVisionLLM(List.of(page.base64), page.pageNum, page.pageNum, total, null);
                    int done = completed.incrementAndGet();
                    if (done % 5 == 0 || done == total) {
                        log.info("Vision PDF 并发进度: {}/{} 页完成", done, total);
                    }
                    return new IndexedResult(page.pageNum, md);
                } finally {
                    semaphore.release();
                }
            }, executor);
            futures.add(f);
        }

        return futures.stream()
                .map(CompletableFuture::join)
                .filter(Objects::nonNull)
                .sorted(Comparator.comparingInt(r -> r.index))
                .map(r -> r.markdown)
                .toList();
    }

    // ======================== 跨页上下文模式 ========================

    List<String> processSequentialWithContext(List<PageImage> pages) {
        int total = pages.size();
        List<String> results = new ArrayList<>();
        String prevTail = null;

        for (PageImage page : pages) {
            String md = callVisionLLM(
                    List.of(page.base64), page.pageNum, page.pageNum, total, prevTail);
            results.add(md);
            prevTail = (md != null && md.length() > contextTailChars)
                    ? md.substring(md.length() - contextTailChars)
                    : md;
            log.info("Vision PDF 串行进度: {}/{} 页完成 (contextTail={}chars)",
                    page.pageNum, total, prevTail != null ? prevTail.length() : 0);
        }
        return results;
    }

    // ======================== Vision LLM 调用（直连 Kimi / 其他 OpenAI 兼容 API） ========================

    /**
     * 调用视觉模型 API，支持重试。
     * <p>
     * 直连模式不带 "openclaw/" 前缀，model 直接传给 API。
     */
    String callVisionLLM(List<String> base64Images, int fromPage, int toPage, int totalPages,
                         String priorContext) {
        List<Map<String, Object>> contentParts = new ArrayList<>();
        for (String b64 : base64Images) {
            contentParts.add(Map.of(
                    "type", "image_url",
                    "image_url", Map.of("url", "data:image/png;base64," + b64, "detail", "high")
            ));
        }
        contentParts.add(Map.of("type", "text", "text", buildPrompt(fromPage, toPage, totalPages, priorContext)));

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", model);  // 直连，不加 openclaw/ 前缀
        body.put("messages", List.of(Map.of("role", "user", "content", contentParts)));
        body.put("temperature", 1);
        body.put("max_tokens", 8192);

        Exception lastError = null;
        for (int attempt = 1; attempt <= RETRY_MAX; attempt++) {
            try {
                @SuppressWarnings("unchecked")
                Map<String, Object> response = restClient.post()
                        .uri("/chat/completions")
                        .body(body)
                        .retrieve()
                        .body(Map.class);

                if (response == null) {
                    lastError = new RuntimeException("空响应");
                    continue;
                }

                @SuppressWarnings("unchecked")
                List<Map<String, Object>> choices = (List<Map<String, Object>>) response.get("choices");
                if (choices == null || choices.isEmpty()) {
                    lastError = new RuntimeException("空 choices: " + response.keySet());
                    continue;
                }

                @SuppressWarnings("unchecked")
                Map<String, Object> msg = (Map<String, Object>) choices.get(0).get("message");
                String content = (String) msg.get("content");

                String finishReason = (String) choices.get(0).get("finish_reason");
                if (finishReason != null && !"stop".equals(finishReason)) {
                    log.warn("Vision LLM finish_reason={} (页{}), content_len={}",
                            finishReason, fromPage, content != null ? content.length() : 0);
                }

                return content != null ? content : "";

            } catch (Exception e) {
                lastError = e;
                if (attempt < RETRY_MAX) {
                    long delay = attempt * 1500L;
                    log.warn("Vision LLM 失败 (attempt={}/{}): {}, {}ms 后重试",
                            attempt, RETRY_MAX, e.getMessage(), delay);
                    try { Thread.sleep(delay); } catch (InterruptedException ie) { Thread.currentThread().interrupt(); break; }
                }
            }
        }

        log.error("Vision LLM 调用失败 (页{}): {}",
                fromPage, lastError != null ? lastError.getMessage() : "未知错误");
        return "";
    }

    // ======================== Prompt ========================

    private String buildPrompt(int fromPage, int toPage, int totalPages, String priorContext) {
        StringBuilder sb = new StringBuilder();

        if (priorContext != null && !priorContext.isEmpty()) {
            sb.append("前一页 Markdown 末尾（仅供参考，用于保持格式连贯）：\n");
            sb.append("```markdown\n").append(priorContext).append("\n```\n\n");
        }

        if (fromPage == toPage) {
            sb.append(String.format("请将这张 PDF 页面（第 %d/%d 页）转为结构良好的 Markdown", fromPage, totalPages));
        } else {
            sb.append(String.format("请将以上 %d 张 PDF 页面（第 %d-%d 页，共 %d 页）转为 Markdown",
                    toPage - fromPage + 1, fromPage, toPage, totalPages));
        }

        sb.append("""


                要求：
                1. **保留文档结构** — 标题用 # / ## / ### 层级，段落间空行
                2. **表格** — 必须用 Markdown 表格（| col1 | col2 |），不省略行列，不转为图片描述
                3. **数学公式** — 行内公式用 $...$，独立公式用 $$...$$（LaTeX 格式）
                4. **代码块** — 用 ``` 包裹并标注语言
                5. **完整保留内容** — 正文、脚注、页眉标题都要保留，不遗漏
                6. **图片描述** — 如果页面中有图片/图表，用 <!-- 图片/图表描述 --> 标注
                7. 严禁在 Markdown 前后输出任何解释、说明、问候语或总结性文字
                8. 输出必须以 Markdown 语法开头，不要任何前缀""");
        return sb.toString();
    }

    // ======================== 工具方法 ========================

    @PreDestroy
    public void shutdown() {
        executor.close();
        log.info("VisionPdfParser 已关闭");
    }

    String encodeToBase64Png(BufferedImage image) throws IOException {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        ImageIO.write(image, "png", bos);
        return Base64.getEncoder().encodeToString(bos.toByteArray());
    }

    record PageImage(int pageNum, String base64) {}

    record IndexedResult(int index, String markdown) {}
}
