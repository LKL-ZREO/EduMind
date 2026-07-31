package com.firedemo.edumind.knowledge;


import com.firedemo.edumind.knowledge.retrieval.DocumentChunk;
import com.firedemo.edumind.shared.exception.BusinessException;
import com.firedemo.edumind.shared.exception.ErrorCode;
import com.firedemo.edumind.auth.authorization.OwnershipGuard;
import com.firedemo.edumind.platform.storage.FileStorage;
import com.firedemo.edumind.knowledge.retrieval.RagResult;
import com.firedemo.edumind.knowledge.retrieval.RagSearchRequest;
import com.firedemo.edumind.knowledge.retrieval.RagService;
import com.firedemo.edumind.knowledge.retrieval.SmartChunkService;
import com.firedemo.edumind.knowledge.retrieval.VectorStoreService;
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
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * 文档服务实现
 *
 * @author 海克斯
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DocumentService {

    private final DocumentMapper documentMapper;
    private final DirectoryNodeMapper directoryNodeMapper;
    private final FileStorage fileStorageService;
    private final SmartChunkService chunkService;
    private final VectorStoreService vectorStoreService;
    private final RagService ragService;
    private final OwnershipGuard ownershipGuard;

    @Value("${storage.upload-dir:${user.home}/.homework-grader/uploads}")
    private String uploadDir;
    @Transactional(rollbackFor = Exception.class)
    public String uploadDocument(Long userId, MultipartFile file, Long kbId) {
        requireAccess(kbId == null || ownershipGuard.canAccessKnowledgeBase(userId, kbId));
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
            throw new BusinessException(ErrorCode.FILE_UPLOAD_ERROR);
        }
    }

    @Transactional(rollbackFor = Exception.class)
    public String uploadDocument(
            Long userId,
            MultipartFile file,
            Long parentNodeId,
            Long kbId) {
        requireAccess(kbId == null || ownershipGuard.canAccessKnowledgeBase(userId, kbId));
        if (parentNodeId != null) {
            DirectoryNode parent = directoryNodeMapper.selectById(parentNodeId);
            if (parent == null || !ownershipGuard.canAccessDirectoryNode(userId, parentNodeId)) {
                throw new IllegalArgumentException("目标目录不存在或无权访问");
            }
            if (!Objects.equals(parent.getKbId(), kbId)) {
                throw new IllegalArgumentException("目标目录与知识库不匹配");
            }
            if (!"folder".equals(parent.getNodeType())) {
                throw new IllegalArgumentException("只能上传到文件夹中");
            }
        }

        String docId = uploadDocument(userId, file, kbId);
        int maxOrder = parentNodeId != null
                ? Optional.ofNullable(directoryNodeMapper.selectByParentId(parentNodeId))
                        .map(nodes -> nodes.stream()
                                .mapToInt(DirectoryNode::getSortOrder)
                                .max()
                                .orElse(-1))
                        .orElse(-1)
                : Optional.ofNullable(kbId == null
                                ? directoryNodeMapper.selectByUserId(userId)
                                : directoryNodeMapper.selectByKbId(kbId))
                        .map(nodes -> nodes.stream()
                                .filter(node -> node.getParentId() == null)
                                .mapToInt(DirectoryNode::getSortOrder)
                                .max()
                                .orElse(-1))
                        .orElse(-1);

        DirectoryNode fileNode = new DirectoryNode();
        fileNode.setUserId(userId);
        fileNode.setParentId(parentNodeId);
        fileNode.setLabel(file.getOriginalFilename());
        fileNode.setNodeType("file");
        fileNode.setDocId(docId);
        fileNode.setKbId(kbId);
        fileNode.setSortOrder(maxOrder + 1);
        directoryNodeMapper.insert(fileNode);
        return docId;
    }
    public Document getByDocId(String docId) {
        return documentMapper.selectByDocId(docId);
    }
    public List<Document> getUserDocuments(Long userId) {
        return documentMapper.selectByUserId(userId);
    }
    @Transactional(rollbackFor = Exception.class)
    public boolean deleteDocument(String docId, Long userId) {
        Document document = documentMapper.selectByDocId(docId);
        if (document == null) {
            return false;
        }
        requireAccess(ownershipGuard.canWriteDocument(userId, docId));

        // 删除向量库中的 chunk
        try {
            vectorStoreService.deleteDocument(docId);
        } catch (RuntimeException e) {
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

            // 4. 保存到向量存储（带用户隔离信息）
            vectorStoreService.saveChunks(docId, chunks, document.getUserId(), document.getKbId());

            // 5. 更新状态
            updateStatus(docId, 1, chunks.size()); // 完成

            log.info("Document processed successfully: {}, chunks: {}", docId, chunks.size());

        } catch (RuntimeException e) {
            log.error("Failed to process document: {}", docId, e);
            updateStatus(docId, 2, null); // 失败
        }
    }




    // ==================== 目录树管理 ====================
    public List<DirectoryNode> getDirectoryTree(Long userId) {
        return directoryNodeMapper.selectByUserId(userId);
    }
    public List<DirectoryNode> getDirectoryTreeByKbId(Long userId, Long kbId) {
        requireAccess(ownershipGuard.canAccessKnowledgeBase(userId, kbId));
        return directoryNodeMapper.selectByKbId(kbId);
    }
    @Transactional(rollbackFor = Exception.class)
    public Long createFolder(Long userId, Long parentId, String label, Long kbId) {
        requireAccess(kbId == null || ownershipGuard.canAccessKnowledgeBase(userId, kbId));
        // 校验父节点存在性及归属
        if (parentId != null) {
            DirectoryNode parent = directoryNodeMapper.selectById(parentId);
            if (parent == null) {
                throw new IllegalArgumentException("父节点不存在");
            }
            requireAccess(ownershipGuard.canAccessDirectoryNode(userId, parentId));
            if (!Objects.equals(parent.getKbId(), kbId))
                throw new IllegalArgumentException("父节点与知识库不匹配");
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
    @Transactional(rollbackFor = Exception.class)
    public void renameNode(Long userId, Long nodeId, String label) {
        DirectoryNode node = directoryNodeMapper.selectById(nodeId);
        if (node == null) {
            throw new IllegalArgumentException("节点不存在");
        }
        requireAccess(ownershipGuard.canAccessDirectoryNode(userId, nodeId));
        node.setLabel(label);
        directoryNodeMapper.updateById(node);
        log.info("Node renamed: id={}, label={}", nodeId, label);
    }
    @Transactional(rollbackFor = Exception.class)
    public void deleteDirectoryNode(Long userId, Long nodeId) {
        DirectoryNode node = directoryNodeMapper.selectById(nodeId);
        if (node == null) {
            throw new IllegalArgumentException("节点不存在");
        }
        requireAccess(ownershipGuard.canAccessDirectoryNode(userId, nodeId));

        // 递归删除所有子节点
        List<DirectoryNode> descendants = directoryNodeMapper.selectDescendants(nodeId);
        for (DirectoryNode child : descendants) {
            // 如果是文件节点，同时删除关联的文档
            if ("file".equals(child.getNodeType()) && child.getDocId() != null) {
                try {
                    deleteDocument(child.getDocId(), userId);
                } catch (RuntimeException e) {
                    log.warn("Failed to delete document {} while deleting node {}", child.getDocId(), child.getId());
                }
            }
            directoryNodeMapper.deleteById(child.getId());
        }

        // 删除自身
        if ("file".equals(node.getNodeType()) && node.getDocId() != null) {
            try {
                deleteDocument(node.getDocId(), userId);
            } catch (RuntimeException e) {
                log.warn("Failed to delete document {} while deleting node {}", node.getDocId(), node.getId());
            }
        }
        directoryNodeMapper.deleteById(nodeId);
        log.info("Directory node deleted: id={}", nodeId);
    }
    @Transactional(rollbackFor = Exception.class)
    public void moveNode(Long userId, Long nodeId, Long targetParentId, Integer sortOrder) {
        DirectoryNode node = directoryNodeMapper.selectById(nodeId);
        if (node == null) {
            throw new IllegalArgumentException("节点不存在");
        }
        requireAccess(ownershipGuard.canAccessDirectoryNode(userId, nodeId));

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
            if (target == null || !ownershipGuard.canAccessDirectoryNode(userId, targetParentId)) {
                throw new IllegalArgumentException("目标节点不存在或无权访问");
            }
            if (!Objects.equals(node.getKbId(), target.getKbId()))
                throw new IllegalArgumentException("不能跨知识库移动节点");
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
    @Transactional(rollbackFor = Exception.class)
    public boolean toggleShare(Long userId, Long nodeId) {
        DirectoryNode node = directoryNodeMapper.selectById(nodeId);
        if (node == null || !Objects.equals(node.getUserId(), userId)) {
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
    public List<Map<String, Object>> getSharedTree(Long userId) {
        return directoryNodeMapper.selectSharedByOthersWithName(userId);
    }

    private void requireAccess(boolean allowed) {
        if (!allowed) throw new BusinessException(ErrorCode.FORBIDDEN);
    }

}
