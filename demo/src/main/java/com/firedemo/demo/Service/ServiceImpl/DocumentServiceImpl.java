package com.firedemo.demo.Service.ServiceImpl;


import com.firedemo.demo.Entity.DirectoryNode;
import com.firedemo.demo.Entity.Document;
import com.firedemo.demo.Entity.DocumentChunk;
import com.firedemo.demo.Service.DocumentService;
import com.firedemo.demo.Service.FileStorageService;
import com.firedemo.demo.mapper.DirectoryNodeMapper;
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
    private final DirectoryNodeMapper directoryNodeMapper;
    private final FileStorageService fileStorageService;
    private final SmartChunkService chunkService;
    private final EmbeddingService embeddingService;
    private final VectorStoreService vectorStoreService;

    @Value("${storage.upload-dir:${user.home}/.homework-grader/uploads}")
    private String uploadDir;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public String uploadDocument(Long userId, MultipartFile file, Long kbId) {
        String docId = UUID.randomUUID().toString().replace("-", "");
        String originalFilename = file.getOriginalFilename();

        try {
            Path uploadPath = kbId != null
                    ? Paths.get(uploadDir, "shared", String.valueOf(kbId))
                    : Paths.get(uploadDir);
            if (!Files.exists(uploadPath)) {
                Files.createDirectories(uploadPath);
            }

            String fileName = docId + "_" + originalFilename;
            Path filePath = uploadPath.resolve(fileName);
            file.transferTo(filePath);

            Document document = new Document();
            document.setUserId(userId);
            document.setDocId(docId);
            document.setDocName(originalFilename);
            document.setFilePath(filePath.toString());
            document.setFileSize(file.getSize());
            document.setContentType(file.getContentType());
            document.setStatus(0);
            document.setKbId(kbId);

            documentMapper.insert(document);

            log.info("Document uploaded: docId={}, userId={}, kbId={}", docId, userId, kbId);
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

        // 删除向量库中的 chunk
        try {
            vectorStoreService.deleteDocument(docId);
        } catch (Exception e) {
            log.warn("Failed to delete chunks for document: {}", docId, e);
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

    // ==================== 目录树管理 ====================

    @Override
    public List<DirectoryNode> getDirectoryTree(Long userId) {
        return directoryNodeMapper.selectByUserId(userId);
    }

    @Override
    public List<DirectoryNode> getDirectoryTreeByKbId(Long kbId) {
        return directoryNodeMapper.selectByKbId(kbId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long createFolder(Long userId, Long parentId, String label, Long kbId) {
        // 校验父节点存在性及归属
        if (parentId != null) {
            DirectoryNode parent = directoryNodeMapper.selectById(parentId);
            if (parent == null) {
                throw new IllegalArgumentException("父节点不存在");
            }
            // 共享知识库下的节点，所有成员可操作
            if (parent.getKbId() == null && !parent.getUserId().equals(userId)) {
                throw new IllegalArgumentException("无权访问");
            }
            if (!"folder".equals(parent.getNodeType())) {
                throw new IllegalArgumentException("只能在文件夹下创建子文件夹");
            }
        }

        // 同级最大排序号
        int maxOrder;
        if (parentId != null) {
            List<DirectoryNode> siblings = directoryNodeMapper.selectByParentId(parentId);
            maxOrder = siblings.stream().mapToInt(DirectoryNode::getSortOrder).max().orElse(-1);
        } else {
            List<DirectoryNode> roots = directoryNodeMapper.selectByUserId(userId).stream()
                    .filter(n -> n.getParentId() == null).toList();
            maxOrder = roots.stream().mapToInt(DirectoryNode::getSortOrder).max().orElse(-1);
        }

        DirectoryNode node = new DirectoryNode();
        node.setUserId(userId);
        node.setParentId(parentId);
        node.setLabel(label);
        node.setNodeType("folder");
        node.setKbId(kbId);
        node.setSortOrder(maxOrder + 1);

        directoryNodeMapper.insert(node);
        log.info("Folder created: id={}, label={}, parentId={}, kbId={}", node.getId(), label, parentId, kbId);
        return node.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void renameNode(Long userId, Long nodeId, String label) {
        DirectoryNode node = directoryNodeMapper.selectById(nodeId);
        if (node == null) {
            throw new IllegalArgumentException("节点不存在");
        }
        if (node.getKbId() == null && !node.getUserId().equals(userId)) {
            throw new IllegalArgumentException("无权操作");
        }
        node.setLabel(label);
        directoryNodeMapper.updateById(node);
        log.info("Node renamed: id={}, label={}", nodeId, label);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteDirectoryNode(Long userId, Long nodeId) {
        DirectoryNode node = directoryNodeMapper.selectById(nodeId);
        if (node == null) {
            throw new IllegalArgumentException("节点不存在");
        }
        if (node.getKbId() == null && !node.getUserId().equals(userId)) {
            throw new IllegalArgumentException("无权操作");
        }

        // 递归删除所有子节点
        List<DirectoryNode> descendants = directoryNodeMapper.selectDescendants(nodeId);
        for (DirectoryNode child : descendants) {
            // 如果是文件节点，同时删除关联的文档
            if ("file".equals(child.getNodeType()) && child.getDocId() != null) {
                try {
                    deleteDocument(child.getDocId(), userId);
                } catch (Exception e) {
                    log.warn("Failed to delete document {} while deleting node {}", child.getDocId(), child.getId());
                }
            }
            directoryNodeMapper.deleteById(child.getId());
        }

        // 删除自身
        if ("file".equals(node.getNodeType()) && node.getDocId() != null) {
            try {
                deleteDocument(node.getDocId(), userId);
            } catch (Exception e) {
                log.warn("Failed to delete document {} while deleting node {}", node.getDocId(), node.getId());
            }
        }
        directoryNodeMapper.deleteById(nodeId);
        log.info("Directory node deleted: id={}", nodeId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void moveNode(Long userId, Long nodeId, Long targetParentId, Integer sortOrder) {
        DirectoryNode node = directoryNodeMapper.selectById(nodeId);
        if (node == null) {
            throw new IllegalArgumentException("节点不存在");
        }
        if (node.getKbId() == null && !node.getUserId().equals(userId)) {
            throw new IllegalArgumentException("无权操作");
        }

        // 不能移到自己或自己的子节点下
        if (targetParentId != null) {
            if (targetParentId.equals(nodeId)) {
                throw new IllegalArgumentException("不能将节点移动到自己下面");
            }
            List<DirectoryNode> descendants = directoryNodeMapper.selectDescendants(nodeId);
            boolean isDescendant = descendants.stream().anyMatch(d -> d.getId().equals(targetParentId));
            if (isDescendant) {
                throw new IllegalArgumentException("不能将节点移动到其子节点下");
            }

            DirectoryNode target = directoryNodeMapper.selectById(targetParentId);
            if (target == null || !target.getUserId().equals(userId)) {
                throw new IllegalArgumentException("目标节点不存在或无权访问");
            }
            if (!"folder".equals(target.getNodeType())) {
                throw new IllegalArgumentException("目标节点不是文件夹");
            }
        }

        node.setParentId(targetParentId);
        if (sortOrder != null) {
            node.setSortOrder(sortOrder);
        }
        directoryNodeMapper.updateById(node);
        log.info("Node moved: id={}, targetParentId={}, sortOrder={}", nodeId, targetParentId, sortOrder);
    }

    // ==================== 共享 ====================

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean toggleShare(Long userId, Long nodeId) {
        DirectoryNode node = directoryNodeMapper.selectById(nodeId);
        if (node == null || !node.getUserId().equals(userId)) {
            throw new IllegalArgumentException("节点不存在或无权操作");
        }
        if (!"folder".equals(node.getNodeType())) {
            throw new IllegalArgumentException("只能共享文件夹");
        }

        boolean newShared = !Boolean.TRUE.equals(node.getIsShared());

        // 更新自身
        node.setIsShared(newShared);
        directoryNodeMapper.updateById(node);

        // 递归更新所有子节点
        List<DirectoryNode> descendants = directoryNodeMapper.selectDescendants(nodeId);
        for (DirectoryNode child : descendants) {
            child.setIsShared(newShared);
            directoryNodeMapper.updateById(child);
        }

        log.info("Folder share toggled: id={}, label={}, shared={}, descendants={}",
                nodeId, node.getLabel(), newShared, descendants.size());
        return newShared;
    }

    @Override
    public List<java.util.Map<String, Object>> getSharedTree(Long userId) {
        return directoryNodeMapper.selectSharedByOthersWithName(userId);
    }

    /**
     * RRF 融合结果
     */
    private record RrfResult(String chunkId, double rrfScore) {}
}
