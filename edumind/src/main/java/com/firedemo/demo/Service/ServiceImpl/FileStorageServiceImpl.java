package com.firedemo.demo.Service.ServiceImpl;

import com.firedemo.demo.common.exception.BusinessException;
import com.firedemo.demo.common.exception.ErrorCode;
import com.firedemo.demo.Service.FileStorageService;
import lombok.extern.slf4j.Slf4j;
import org.apache.tika.exception.TikaException;
import org.apache.tika.extractor.EmbeddedDocumentExtractor;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.parser.AutoDetectParser;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.parser.Parser;
import org.apache.tika.parser.pdf.PDFParserConfig;
import org.apache.tika.sax.BodyContentHandler;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.Set;
import java.util.UUID;

@Slf4j
@Service
@ConditionalOnProperty(name = "storage.type", havingValue = "local", matchIfMissing = true)
public class FileStorageServiceImpl implements FileStorageService {

    @Value("${storage.upload-dir}")
    private String uploadDir;

    /** 允许上传的文件扩展名 */
    private static final Set<String> ALLOWED_EXTENSIONS = Set.of(
            "txt", "md", "pdf", "doc", "docx", "ppt", "pptx", "xls", "xlsx",
            "c", "cpp", "h", "java", "py", "js", "ts", "html", "css",
            "png", "jpg", "jpeg", "gif", "svg", "bmp"
    );

    /** 允许上传的 MIME 类型前缀 */
    private static final Set<String> ALLOWED_MIME_PREFIXES = Set.of(
            "text/", "application/pdf",
            "application/msword",
            "application/vnd.openxmlformats-officedocument.wordprocessingml",
            "application/vnd.ms-powerpoint",
            "application/vnd.openxmlformats-officedocument.presentationml",
            "application/vnd.ms-excel",
            "application/vnd.openxmlformats-officedocument.spreadsheetml",
            "image/png", "image/jpeg", "image/gif", "image/svg", "image/bmp"
    );

    /** 最大文件大小: 50MB */
    private static final long MAX_FILE_SIZE = 50 * 1024 * 1024;

    /**
     * 校检路径是否在 uploadDir 内，防止路径穿越攻击
     */
    private Path validatePath(String filePath) {
        try {
            Path uploadRoot = Paths.get(uploadDir).toRealPath();
            Path resolved = uploadRoot.resolve(Paths.get(filePath)).normalize().toRealPath();
            if (!resolved.startsWith(uploadRoot)) {
                log.warn("路径穿越尝试: {} -> {}", filePath, resolved);
                throw new SecurityException("Path traversal attempt detected");
            }
            return resolved;
        } catch (SecurityException e) {
            throw e;
        } catch (IOException e) {
            log.error("路径校验失败: {}", filePath, e);
            throw new BusinessException(ErrorCode.FILE_NOT_FOUND);
        }
    }

    @Override
    @io.micrometer.core.annotation.Timed(value = "file.upload", histogram = true)
    public String storeFile(MultipartFile file) {
        // 文件类型白名单校验
        validateFileType(file);

        try {
            Path uploadPath = Paths.get(uploadDir);
            if (!Files.exists(uploadPath)) {
                Files.createDirectories(uploadPath);
            }

            // 清理文件名中的路径分隔符，防穿越
            String originalName = file.getOriginalFilename();
            if (originalName != null) {
                originalName = originalName.replaceAll("[/\\\\]", "_");
            }
            String fileName = UUID.randomUUID() + "_" + originalName;
            Path targetPath = uploadPath.resolve(fileName);

            Files.copy(file.getInputStream(), targetPath, StandardCopyOption.REPLACE_EXISTING);

            log.info("文件保存成功: {}", targetPath);
            return targetPath.toString();
        } catch (IOException e) {
            log.error("文件保存失败", e);
            throw new BusinessException(ErrorCode.FILE_UPLOAD_ERROR);
        }
    }

    @Override
    public void deleteFile(String filePath) {
        try {
            Path path = validatePath(filePath);
            Files.deleteIfExists(path);
            log.info("文件删除成功: {}", path);
        } catch (IOException e) {
            log.error("文件删除失败: {}", filePath, e);
        }
    }

    @Override
    public String readFileContent(String filePath) {
        try {
            // 路径穿越防护：校检文件在 uploadDir 内
            Path path = validatePath(filePath);

            // 先用 Tika 尝试解析所有格式
            String parsedContent = parseWithTika(path);
            if (parsedContent != null && !parsedContent.isEmpty()) {
                return parsedContent;
            }

            // Tika 解析失败，回退到文本读取（尝试多编码）
            String contentType = Files.probeContentType(path);
            if (contentType != null && contentType.startsWith("text")) {
                byte[] bytes = Files.readAllBytes(path);
                String[] charsets = {"UTF-8", "GBK", "GB18030", "ISO-8859-1"};
                for (String cs : charsets) {
                    try {
                        return new String(bytes, java.nio.charset.Charset.forName(cs));
                    } catch (java.nio.charset.UnsupportedCharsetException ignored) {
                    }
                }
                return new String(bytes);
            } else {
                return "[文件类型: " + contentType + ", 路径: " + filePath + "]";
            }
        } catch (IOException e) {
            log.error("读取文件失败: {}", filePath, e);
            throw new BusinessException(ErrorCode.FILE_READ_ERROR);
        }
    }

    /**
     * 校验文件类型（扩展名 + MIME 双重校验）
     */
    private void validateFileType(MultipartFile file) {
        String originalName = file.getOriginalFilename();

        // 1. 文件大小校验
        if (file.getSize() > MAX_FILE_SIZE) {
            throw new BusinessException(ErrorCode.FILE_UPLOAD_ERROR.getCode(),
                    "文件大小超过限制（最大 50MB）");
        }

        // 2. 扩展名校验
        if (originalName != null && originalName.contains(".")) {
            String ext = originalName.substring(originalName.lastIndexOf('.') + 1).toLowerCase();
            if (!ALLOWED_EXTENSIONS.contains(ext)) {
                log.warn("不允许的文件类型: {}", ext);
                throw new BusinessException(ErrorCode.FILE_UPLOAD_ERROR.getCode(),
                        "不支持的文件类型: ." + ext);
            }
        }

        // 3. MIME 类型校验
        String contentType = file.getContentType();
        if (contentType != null) {
            boolean mimeAllowed = ALLOWED_MIME_PREFIXES.stream().anyMatch(contentType::startsWith);
            if (!mimeAllowed) {
                log.warn("不允许的 MIME 类型: {}", contentType);
                throw new BusinessException(ErrorCode.FILE_UPLOAD_ERROR.getCode(),
                        "不支持的文件类型: " + contentType);
            }
        }
    }

    /**
     * 使用 Apache Tika 解析文档内容
     * 支持 PDF、Word、Excel、PPT、TXT、MD 等多种格式
     */
    private String parseWithTika(Path filePath) {
        try (InputStream inputStream = Files.newInputStream(filePath)) {
            // 1. 创建自动检测解析器
            AutoDetectParser parser = new AutoDetectParser();

            // 2. 创建内容处理器，限制最大 5MB
            BodyContentHandler handler = new BodyContentHandler(5 * 1024 * 1024);

            // 3. 创建元数据对象
            Metadata metadata = new Metadata();

            // 4. 创建解析上下文
            ParseContext context = new ParseContext();
            context.set(Parser.class, parser);

            // 5. 禁用嵌入文档解析（避免提取图片引用）
            context.set(EmbeddedDocumentExtractor.class, 
                new NoOpEmbeddedDocumentExtractor());

            // 6. PDF 配置：关闭图片提取，按位置排序文本
            PDFParserConfig pdfConfig = new PDFParserConfig();
            pdfConfig.setExtractInlineImages(false);
            pdfConfig.setSortByPosition(true);
            context.set(PDFParserConfig.class, pdfConfig);

            // 7. 执行解析
            parser.parse(inputStream, handler, metadata, context);

            String content = handler.toString();
            log.info("Tika 解析成功: {}, 提取 {} 字符", filePath.getFileName(), content.length());
            return content;

        } catch (TikaException | SAXException e) {
            log.warn("Tika 解析失败: {}, 错误: {}", filePath.getFileName(), e.getMessage());
            return null;
        } catch (IOException e) {
            log.error("读取文件失败: {}", filePath, e);
            return null;
        }
    }

    /**
     * 空操作嵌入文档提取器（禁用嵌入资源解析）
     */
    private static class NoOpEmbeddedDocumentExtractor implements EmbeddedDocumentExtractor {
        @Override
        public boolean shouldParseEmbedded(Metadata metadata) {
            return false;
        }

        @Override
        public void parseEmbedded(InputStream inputStream, ContentHandler contentHandler, Metadata metadata, boolean b) throws SAXException, IOException {

        }

    }
}