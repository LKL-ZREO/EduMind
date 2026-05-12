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
import java.util.*;

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

    // RRF 融合参数，k=60 是学术论文和 Elasticsearch 的标准默认值
    private static final int RRF_K = 60;
    // 每路检索的候选数（多取一些参与融合，最后截断到 topK）
    private static final int CANDIDATE_MULTIPLIER = 3;

    @Override
    public List<String> searchRelevantContent(String query, int topK) {
        // ===== 1. 语义向量检索（第一路）=====
        float[] queryEmbedding = embeddingService.embedQuery(query);
        List<DocumentChunk> vectorResults = vectorStoreService.similaritySearch(
                queryEmbedding, topK * CANDIDATE_MULTIPLIER);
        Map<String, Double> semanticRanks = rankByCosine(queryEmbedding, vectorResults);

        // ===== 2. 关键词检索（第二路）=====
        List<VectorStoreService.ScoredChunk> keywordResults = vectorStoreService.keywordSearch(
                query, topK * CANDIDATE_MULTIPLIER);
        Map<String, Double> keywordRanks = rankKeyword(keywordResults);

        // ===== 3. RRF 融合 =====
        Set<String> allChunkIds = new LinkedHashSet<>();
        allChunkIds.addAll(semanticRanks.keySet());
        allChunkIds.addAll(keywordRanks.keySet());

        Map<String, DocumentChunk> chunkMap = buildChunkMap(vectorResults, keywordResults);

        return allChunkIds.stream()
                .map(id -> {
                    double rrfScore = 0.0;
                    Double semRank = semanticRanks.get(id);
                    Double kwRank = keywordRanks.get(id);
                    if (semRank != null) rrfScore += 1.0 / (RRF_K + semRank);
                    if (kwRank != null) rrfScore += 1.0 / (RRF_K + kwRank);
                    return new RrfResult(id, rrfScore);
                })
                .sorted((a, b) -> Double.compare(b.rrfScore, a.rrfScore))
                .limit(topK)
                .map(r -> chunkMap.get(r.chunkId))
                .filter(Objects::nonNull)
                .map(DocumentChunk::getContent)
                .toList();
    }

    /**
     * 语义路打分：按余弦相似度降序排列，rank 从 1 开始
     */
    private Map<String, Double> rankByCosine(float[] queryEmbedding, List<DocumentChunk> chunks) {
        Map<String, Double> rankMap = new LinkedHashMap<>();
        List<VectorStoreService.ScoredChunk> scored = chunks.stream()
                .map(c -> {
                    double sim = cosineSimilarity(queryEmbedding, c.getEmbedding());
                    return new VectorStoreService.ScoredChunk(c, sim);
                })
                .sorted((a, b) -> Double.compare(b.score(), a.score()))
                .toList();

        for (int i = 0; i < scored.size(); i++) {
            rankMap.put(scored.get(i).chunk().getId(), (double) (i + 1));
        }
        return rankMap;
    }

    /**
     * 关键词路打分：按 keyword_score 降序排列，rank 从 1 开始
     */
    private Map<String, Double> rankKeyword(List<VectorStoreService.ScoredChunk> results) {
        Map<String, Double> rankMap = new LinkedHashMap<>();
        for (int i = 0; i < results.size(); i++) {
            rankMap.put(results.get(i).chunk().getId(), (double) (i + 1));
        }
        return rankMap;
    }

    /**
     * 构建 chunkId → DocumentChunk 映射
     */
    private Map<String, DocumentChunk> buildChunkMap(List<DocumentChunk> vectorResults,
                                                      List<VectorStoreService.ScoredChunk> keywordResults) {
        Map<String, DocumentChunk> map = new HashMap<>();
        for (DocumentChunk c : vectorResults) {
            map.putIfAbsent(c.getId(), c);
        }
        for (VectorStoreService.ScoredChunk sc : keywordResults) {
            map.putIfAbsent(sc.chunk().getId(), sc.chunk());
        }
        return map;
    }

    /**
     * 计算余弦相似度
     */
    private double cosineSimilarity(float[] a, float[] b) {
        if (a == null || b == null || a.length != b.length) return 0;

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
     * RRF 融合结果
     */
    private record RrfResult(String chunkId, double rrfScore) {}
}
