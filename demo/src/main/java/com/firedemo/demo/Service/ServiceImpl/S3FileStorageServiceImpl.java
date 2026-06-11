package com.firedemo.demo.Service.ServiceImpl;

import com.firedemo.demo.Service.FileStorageService;
import com.firedemo.demo.common.exception.BusinessException;
import com.firedemo.demo.common.exception.ErrorCode;
import com.firedemo.demo.config.properties.S3Properties;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.apache.tika.exception.TikaException;
import org.apache.tika.extractor.EmbeddedDocumentExtractor;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.parser.AutoDetectParser;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.parser.Parser;
import org.apache.tika.parser.pdf.PDFParserConfig;
import org.apache.tika.sax.BodyContentHandler;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.*;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

/**
 * S3 对象存储实现（兼容 MinIO / 阿里云 OSS / AWS S3 / 腾讯云 COS）
 * <p>
 * 启用条件：storage.type=s3
 */
@Slf4j
@Service
@ConditionalOnProperty(name = "storage.type", havingValue = "s3")
public class S3FileStorageServiceImpl implements FileStorageService {

    private final S3Properties s3Properties;
    private S3Client s3Client;

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("yyyy/MM");

    public S3FileStorageServiceImpl(S3Properties s3Properties) {
        this.s3Properties = s3Properties;
    }

    @PostConstruct
    void init() {
        this.s3Client = S3Client.builder()
                .region(Region.of(s3Properties.getRegion()))
                .endpointOverride(URI.create(s3Properties.getEndpoint()))
                .credentialsProvider(StaticCredentialsProvider.create(
                        AwsBasicCredentials.create(
                                s3Properties.getAccessKey(),
                                s3Properties.getSecretKey())))
                .forcePathStyle(s3Properties.isPathStyleEnabled())
                .build();

        // 自动创建 Bucket（不存在时）
        try {
            s3Client.headBucket(HeadBucketRequest.builder()
                    .bucket(s3Properties.getBucket()).build());
        } catch (NoSuchBucketException e) {
            s3Client.createBucket(CreateBucketRequest.builder()
                    .bucket(s3Properties.getBucket()).build());
            log.info("S3 Bucket 已自动创建: {}", s3Properties.getBucket());
        }

        log.info("S3 客户端初始化完成: endpoint={}, bucket={}, pathStyle={}",
                s3Properties.getEndpoint(), s3Properties.getBucket(),
                s3Properties.isPathStyleEnabled());
    }

    @PreDestroy
    void destroy() {
        if (s3Client != null) {
            s3Client.close();
        }
    }

    @Override
    public String storeFile(MultipartFile file) {
        String datePath = LocalDate.now().format(DATE_FMT);
        String objectKey = String.format("homework/%s/%s_%s",
                datePath,
                UUID.randomUUID().toString().substring(0, 8),
                file.getOriginalFilename());

        try (InputStream inputStream = file.getInputStream()) {
            s3Client.putObject(
                    PutObjectRequest.builder()
                            .bucket(s3Properties.getBucket())
                            .key(objectKey)
                            .contentType(file.getContentType())
                            .build(),
                    RequestBody.fromInputStream(inputStream, file.getSize()));

            log.info("S3 上传成功: bucket={}, key={}, size={}",
                    s3Properties.getBucket(), objectKey, file.getSize());
            return objectKey;
        } catch (IOException e) {
            log.error("S3 上传失败: {}", objectKey, e);
            throw new BusinessException(ErrorCode.FILE_UPLOAD_ERROR);
        }
    }

    @Override
    public void deleteFile(String filePath) {
        if (filePath == null || filePath.isEmpty()) return;
        try {
            s3Client.deleteObject(DeleteObjectRequest.builder()
                    .bucket(s3Properties.getBucket())
                    .key(filePath)
                    .build());
            log.info("S3 删除成功: bucket={}, key={}", s3Properties.getBucket(), filePath);
        } catch (Exception e) {
            log.error("S3 删除失败: key={}", filePath, e);
        }
    }

    @Override
    public String readFileContent(String filePath) {
        if (filePath == null || filePath.isEmpty()) return "";

        // 先用 Tika 解析
        try (var response = s3Client.getObject(GetObjectRequest.builder()
                .bucket(s3Properties.getBucket())
                .key(filePath)
                .build())) {

            String parsed = parseWithTika(response, filePath);
            if (parsed != null && !parsed.isEmpty()) {
                return parsed;
            }

            // Tika 解析失败，回退到纯文本读取
            byte[] bytes = response.readAllBytes();
            String[] charsets = {"UTF-8", "GBK", "GB18030", "ISO-8859-1"};
            for (String cs : charsets) {
                try {
                    return new String(bytes, java.nio.charset.Charset.forName(cs));
                } catch (Exception ignored) {}
            }
            return new String(bytes);

        } catch (NoSuchKeyException e) {
            log.warn("S3 文件不存在: key={}", filePath);
            return "";
        } catch (Exception e) {
            log.error("S3 读取文件失败: key={}", filePath, e);
            throw new BusinessException(ErrorCode.FILE_READ_ERROR);
        }
    }

    /**
     * 使用 Apache Tika 解析 S3 文件流
     */
    private String parseWithTika(InputStream inputStream, String filePath) {
        try {
            AutoDetectParser parser = new AutoDetectParser();
            BodyContentHandler handler = new BodyContentHandler(5 * 1024 * 1024);
            Metadata metadata = new Metadata();
            ParseContext context = new ParseContext();
            context.set(Parser.class, parser);
            context.set(EmbeddedDocumentExtractor.class, new NoOpEmbeddedDocumentExtractor());

            PDFParserConfig pdfConfig = new PDFParserConfig();
            pdfConfig.setExtractInlineImages(false);
            pdfConfig.setSortByPosition(true);
            context.set(PDFParserConfig.class, pdfConfig);

            parser.parse(inputStream, handler, metadata, context);

            String content = handler.toString();
            String fileName = filePath != null ? filePath.substring(filePath.lastIndexOf('/') + 1) : "?";
            log.info("Tika 解析成功: {}, 提取 {} 字符", fileName, content.length());
            return content;

        } catch (TikaException | SAXException e) {
            log.warn("Tika 解析失败: {}, 错误: {}", filePath, e.getMessage());
            return null;
        } catch (IOException e) {
            log.error("读取 S3 流失败: {}", filePath, e);
            return null;
        }
    }

    private static class NoOpEmbeddedDocumentExtractor implements EmbeddedDocumentExtractor {
        @Override
        public boolean shouldParseEmbedded(Metadata metadata) {
            return false;
        }

        @Override
        public void parseEmbedded(InputStream inputStream, ContentHandler contentHandler,
                                  Metadata metadata, boolean b) throws SAXException, IOException {
        }
    }
}
