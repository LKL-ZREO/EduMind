package com.firedemo.demo.Controller;

import com.firedemo.demo.Entity.DirectoryNode;
import com.firedemo.demo.Entity.Document;
import com.firedemo.demo.Service.DocumentService;
import com.firedemo.demo.Service.FileStorageService;
import com.firedemo.demo.mapper.DirectoryNodeMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 文档管理控制器
 */
@Slf4j
@RestController
@RequestMapping("/api/documents")
@RequiredArgsConstructor
public class DocumentController {

    private final DocumentService documentService;
    private final FileStorageService fileStorageService;
    private final DirectoryNodeMapper directoryNodeMapper;

    /**
     * 上传文档并自动处理（切割+向量化）
     */
    @PostMapping("/upload")
    public ResponseEntity<Map<String, Object>> uploadDocument(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "parentNodeId", required = false) Long parentNodeId,
            @RequestParam(value = "kbId", required = false) Long kbId) {

        Long userId = getCurrentUserId();
        if (userId == null) return ResponseEntity.status(401).build();

        try {
            String docId = documentService.uploadDocument(userId, file, kbId);

            Long targetParentId = parentNodeId != null ? parentNodeId : null;
            if (parentNodeId != null) {
                DirectoryNode parent = directoryNodeMapper.selectById(parentNodeId);
                if (parent == null || !parent.getUserId().equals(userId)) {
                    throw new IllegalArgumentException("目标目录不存在或无权访问");
                }
                if (!"folder".equals(parent.getNodeType())) {
                    throw new IllegalArgumentException("只能上传到文件夹中");
                }
            }

            int maxOrder = parentNodeId != null
                ? Optional.ofNullable(directoryNodeMapper.selectByParentId(parentNodeId))
                    .map(list -> list.stream().mapToInt(DirectoryNode::getSortOrder).max().orElse(-1))
                    .orElse(-1)
                : Optional.ofNullable(directoryNodeMapper.selectByUserId(userId))
                    .map(list -> list.stream().filter(n -> n.getParentId() == null).mapToInt(DirectoryNode::getSortOrder).max().orElse(-1))
                    .orElse(-1);

            DirectoryNode fileNode = new DirectoryNode();
            fileNode.setUserId(userId);
            fileNode.setParentId(targetParentId);
            fileNode.setLabel(file.getOriginalFilename());
            fileNode.setNodeType("file");
            fileNode.setDocId(docId);
            fileNode.setKbId(kbId);
            fileNode.setSortOrder(maxOrder + 1);
            directoryNodeMapper.insert(fileNode);

            documentService.processDocument(docId);

            Map<String, Object> result = new HashMap<>();
            result.put("docId", docId);
            result.put("message", "文档上传成功，正在处理中");
            result.put("status", "processing");
            return ResponseEntity.ok(result);

        } catch (Exception e) {
            log.error("Document upload failed", e);
            return ResponseEntity.badRequest().body(Map.of("error", "上传失败: " + e.getMessage()));
        }
    }

    @GetMapping("/list")
    public ResponseEntity<List<Document>> listDocuments() {
        Long userId = getCurrentUserId();
        if (userId == null) return ResponseEntity.status(401).build();
        return ResponseEntity.ok(documentService.getUserDocuments(userId));
    }

    @DeleteMapping("/{docId}")
    @PreAuthorize("@sec.isDocumentOwner(#docId)")
    public ResponseEntity<Map<String, String>> deleteDocument(@PathVariable String docId) {
        Long userId = getCurrentUserId();
        boolean success = documentService.deleteDocument(docId, userId);
        if (success) return ResponseEntity.ok(Map.of("message", "删除成功"));
        return ResponseEntity.badRequest().body(Map.of("error", "删除失败"));
    }

    @GetMapping("/{docId}/content")
    public ResponseEntity<String> getDocumentContent(@PathVariable String docId) {
        Long userId = getCurrentUserId();
        if (userId == null) return ResponseEntity.status(401).build();

        Document document = documentService.getByDocId(docId);
        if (document == null) return ResponseEntity.status(404).build();

        boolean isOwner = document.getUserId().equals(userId);
        boolean isShared = !isOwner && directoryNodeMapper.selectCount(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<DirectoryNode>()
                    .eq(DirectoryNode::getDocId, docId)
                    .eq(DirectoryNode::getIsShared, true)) > 0;
        boolean isKbDoc = document.getKbId() != null;
        if (!isOwner && !isShared && !isKbDoc) {
            return ResponseEntity.status(403).build();
        }

        try {
            String content = fileStorageService.readFileContent(document.getFilePath());
            return ResponseEntity.ok(content);
        } catch (Exception e) {
            log.warn("Failed to read document content: {}", docId, e);
            return ResponseEntity.status(500).body("无法读取文档内容");
        }
    }

    @PostMapping("/{docId}/process")
    @PreAuthorize("@sec.isDocumentOwner(#docId)")
    public ResponseEntity<Map<String, String>> processDocument(@PathVariable String docId) {
        documentService.processDocument(docId);
        return ResponseEntity.ok(Map.of("message", "文档处理已启动"));
    }

    @GetMapping("/directory/tree")
    public ResponseEntity<List<DirectoryNode>> getDirectoryTree(
            @RequestParam(value = "kbId", required = false) Long kbId) {
        Long userId = getCurrentUserId();
        if (userId == null) return ResponseEntity.status(401).build();
        if (kbId != null) return ResponseEntity.ok(documentService.getDirectoryTreeByKbId(kbId));
        return ResponseEntity.ok(documentService.getDirectoryTree(userId));
    }

    @PostMapping("/directory/folder")
    public ResponseEntity<Map<String, Object>> createFolder(@RequestBody Map<String, Object> body) {
        Long userId = getCurrentUserId();
        if (userId == null) return ResponseEntity.status(401).build();

        Long parentId = body.get("parentId") != null ? Long.valueOf(body.get("parentId").toString()) : null;
        String label = (String) body.get("label");
        Long kbId = body.get("kbId") != null ? Long.valueOf(body.get("kbId").toString()) : null;
        if (label == null || label.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "文件夹名称不能为空"));
        }

        Long nodeId = documentService.createFolder(userId, parentId, label.trim(), kbId);
        return ResponseEntity.ok(Map.of("id", nodeId, "message", "创建成功"));
    }

    @PutMapping("/directory/{id}/rename")
    @PreAuthorize("@sec.isDirectoryNodeOwner(#id)")
    public ResponseEntity<Map<String, String>> renameNode(@PathVariable Long id,
                                                           @RequestBody Map<String, String> body) {
        Long userId = getCurrentUserId();
        String label = body.get("label");
        if (label == null || label.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "名称不能为空"));
        }
        documentService.renameNode(userId, id, label.trim());
        return ResponseEntity.ok(Map.of("message", "重命名成功"));
    }

    @DeleteMapping("/directory/{id}")
    @PreAuthorize("@sec.isDirectoryNodeOwner(#id)")
    public ResponseEntity<Map<String, String>> deleteDirectoryNode(@PathVariable Long id) {
        Long userId = getCurrentUserId();
        documentService.deleteDirectoryNode(userId, id);
        return ResponseEntity.ok(Map.of("message", "删除成功"));
    }

    @PutMapping("/directory/{id}/move")
    @PreAuthorize("@sec.isDirectoryNodeOwner(#id)")
    public ResponseEntity<Map<String, String>> moveNode(@PathVariable Long id,
                                                         @RequestBody Map<String, Object> body) {
        Long userId = getCurrentUserId();
        Long targetParentId = body.get("targetParentId") != null
                ? Long.valueOf(body.get("targetParentId").toString()) : null;
        Integer sortOrder = body.get("sortOrder") != null
                ? Integer.valueOf(body.get("sortOrder").toString()) : null;
        documentService.moveNode(userId, id, targetParentId, sortOrder);
        return ResponseEntity.ok(Map.of("message", "移动成功"));
    }

    @PutMapping("/directory/{id}/share")
    @PreAuthorize("@sec.isDirectoryNodeOwner(#id)")
    public ResponseEntity<Map<String, Object>> toggleShare(@PathVariable Long id) {
        Long userId = getCurrentUserId();
        boolean shared = documentService.toggleShare(userId, id);
        return ResponseEntity.ok(Map.of("shared", shared, "message", shared ? "已共享" : "已取消共享"));
    }

    @GetMapping("/directory/shared")
    public ResponseEntity<List<Map<String, Object>>> getSharedTree() {
        Long userId = getCurrentUserId();
        if (userId == null) return ResponseEntity.status(401).build();
        return ResponseEntity.ok(documentService.getSharedTree(userId));
    }

    // ========== 内部工具 ==========

    private Long getCurrentUserId() {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || auth.getDetails() == null) return null;
        if (auth.getDetails() instanceof Long uid) return uid;
        return null;
    }
}
