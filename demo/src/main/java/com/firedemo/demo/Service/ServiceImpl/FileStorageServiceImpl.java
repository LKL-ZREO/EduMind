package com.firedemo.demo.Service.ServiceImpl;

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
import java.util.UUID;

@Slf4j
@Service
public class FileStorageServiceImpl implements FileStorageService {

    @Value("${storage.upload-dir}")
    private String uploadDir;

    @Override
    public String storeFile(MultipartFile file) {
        try {
            Path uploadPath = Paths.get(uploadDir);
            if (!Files.exists(uploadPath)) {
                Files.createDirectories(uploadPath);
            }

            String fileName = UUID.randomUUID() + "_" + file.getOriginalFilename();
            Path targetPath = uploadPath.resolve(fileName);

            Files.copy(file.getInputStream(), targetPath, StandardCopyOption.REPLACE_EXISTING);

            log.info("文件保存成功: {}", targetPath);
            return targetPath.toString();
        } catch (IOException e) {
            log.error("文件保存失败", e);
            throw new RuntimeException("文件上传失败", e);
        }
    }

    @Override
    public void deleteFile(String filePath) {
        try {
            Files.deleteIfExists(Paths.get(filePath));
            log.info("文件删除成功: {}", filePath);
        } catch (IOException e) {
            log.error("文件删除失败: {}", filePath, e);
        }
    }

    @Override
    public String readFileContent(String filePath) {
        try {
            Path path = Paths.get(filePath);

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
                    } catch (Exception ignored) {
                    }
                }
                return new String(bytes);
            } else {
                return "[文件类型: " + contentType + ", 路径: " + filePath + "]";
            }
        } catch (IOException e) {
            log.error("读取文件失败: {}", filePath, e);
            throw new RuntimeException("读取文件失败", e);
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