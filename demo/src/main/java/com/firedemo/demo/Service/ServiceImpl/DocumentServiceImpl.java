package com.firedemo.demo.Service.ServiceImpl;


import com.firedemo.demo.Entity.Document;
import com.firedemo.demo.Entity.DocumentChunk;
import com.firedemo.demo.Service.DocumentService;
import com.firedemo.demo.Service.FileStorageService;
import com.firedemo.demo.mapper.DocumentMapper;
import com.firedemo.demo.rag.EmbeddingService;
import com.firedemo.demo.rag.SmartChunkService;
import com.firedemo.demo.rag.VectorStoreService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * 文档服务实现
 *
 * @author 海克斯
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DocumentServiceImpl implements DocumentService {

    private final DocumentMapper documentMapper;
    private final FileStorageService fileStorageService;
    private final SmartChunkService chunkService;
    private final EmbeddingService embeddingService;
    private final VectorStoreService vectorStoreService;

    @Value("${storage.upload-dir:${user.home}/.homework-grader/uploads}")
    private String uploadDir;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public String uploadDocument(Long userId, MultipartFile file) {
        String docId = UUID.randomUUID().toString().replace("-", "");
        String originalFilename = file.getOriginalFilename();

        try {
            // 保存文件
            Path uploadPath = Paths.get(uploadDir);
            if (!Files.exists(uploadPath)) {
                Files.createDirectories(uploadPath);
            }

            String fileName = docId + "_" + originalFilename;
            Path filePath = uploadPath.resolve(fileName);
            file.transferTo(filePath);

            // 保存记录
            Document document = new Document();
            document.setUserId(userId);
            document.setDocId(docId);
            document.setDocName(originalFilename);
            document.setFilePath(filePath.toString());
            document.setFileSize(file.getSize());
            document.setContentType(file.getContentType());
            document.setStatus(0); // 处理中

            documentMapper.insert(document);

            log.info("Document uploaded: docId={}, userId={}", docId, userId);
            return docId;

        } catch (IOException e) {
            log.error("Failed to upload document", e);
            throw new RuntimeException("文件上传失败", e);
        }
    }

    @Override
    public Document getByDocId(String docId) {
        return documentMapper.selectByDocId(docId);
    }

    @Override
    public List<Document> getUserDocuments(Long userId) {
        return documentMapper.selectByUserId(userId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean deleteDocument(String docId, Long userId) {
        Document document = documentMapper.selectByDocId(docId);
        if (document == null || !document.getUserId().equals(userId)) {
            return false;
        }

        // 删除文件
        try {
            Files.deleteIfExists(Paths.get(document.getFilePath()));
        } catch (IOException e) {
            log.warn("Failed to delete file: {}", document.getFilePath(), e);
        }

        // 删除记录
        return documentMapper.deleteById(document.getId()) > 0;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean updateStatus(String docId, Integer status, Integer chunkCount) {
        Document document = documentMapper.selectByDocId(docId);
        if (document == null) {
            return false;
        }

        document.setStatus(status);
        if (chunkCount != null) {
            document.setChunkCount(chunkCount);
        }

        return documentMapper.updateById(document) > 0;
    }

    @Override
    @Async
    public void processDocument(String docId) {
        Document document = documentMapper.selectByDocId(docId);
        if (document == null) {
            log.error("Document not found: {}", docId);
            return;
        }

        try {
            log.info("Processing document: {}", docId);

            // 1. 读取文件内容
            String content = fileStorageService.readFileContent(document.getFilePath());
            if (content == null || content.isEmpty()) {
                log.warn("Empty content for document: {}", docId);
                updateStatus(docId, 2, null); // 失败
                return;
            }

            // 2. 智能切割
            List<DocumentChunk> chunks = chunkService.chunk(content, SmartChunkService.ChunkConfig.defaultConfig());

            // 3. 设置文档信息
            chunks.forEach(c -> c.setDocumentName(document.getDocName()));

            // 4. 保存到向量存储
            vectorStoreService.saveChunks(docId, chunks);

            // 5. 更新状态
            updateStatus(docId, 1, chunks.size()); // 完成

            log.info("Document processed successfully: {}, chunks: {}", docId, chunks.size());

        } catch (Exception e) {
            log.error("Failed to process document: " + docId, e);
            updateStatus(docId, 2, null); // 失败
        }
    }

    // 相似度阈值，低于此值的结果将被过滤
    private static final double SIMILARITY_THRESHOLD = 0.75;

    @Override
    public List<String> searchRelevantContent(String query, int topK) {
        // 1. 生成查询向量
        float[] queryEmbedding = embeddingService.embed(query);

        // 2. 从向量存储中搜索（多查一些用于过滤）
        List<DocumentChunk> results = vectorStoreService.similaritySearch(queryEmbedding, topK * 2);

        // 3. 计算相似度并过滤低相似度结果
        return results.stream()
                .map(chunk -> new ScoredChunk(chunk, cosineSimilarity(queryEmbedding, chunk.getEmbedding())))
                .filter(sc -> sc.score >= SIMILARITY_THRESHOLD)
                .sorted((a, b) -> Double.compare(b.score, a.score))
                .limit(topK)
                .map(sc -> sc.chunk.getContent())
                .toList();
    }

    /**
     * 计算余弦相似度
     */
    private double cosineSimilarity(float[] a, float[] b) {
        if (a.length != b.length) return 0;

        double dot = 0;
        double normA = 0;
        double normB = 0;

        for (int i = 0; i < a.length; i++) {
            dot += a[i] * b[i];
            normA += a[i] * a[i];
            normB += b[i] * b[i];
        }

        if (normA == 0 || normB == 0) return 0;
        return dot / (Math.sqrt(normA) * Math.sqrt(normB));
    }

    /**
     * 带分数的文档块
     */
    private record ScoredChunk(DocumentChunk chunk, double score) {}
}
