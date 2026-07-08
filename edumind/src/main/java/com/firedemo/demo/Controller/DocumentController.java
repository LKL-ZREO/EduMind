package com.firedemo.demo.Controller;

import com.firedemo.demo.Entity.DirectoryNode;
import com.firedemo.demo.Entity.Document;
import com.firedemo.demo.Service.DocumentService;
import com.firedemo.demo.Service.FileStorageService;
import com.firedemo.demo.mapper.DirectoryNodeMapper;
import com.firedemo.demo.utils.JwtUtil;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 文档管理控制器
 * 支持文档上传、列表查询、删除，以及RAG向量化处理
 */
@Slf4j
@RestController
@RequestMapping("/api/documents")
@RequiredArgsConstructor
public class DocumentController {

    private final DocumentService documentService;
    private final FileStorageService fileStorageService;
    private final DirectoryNodeMapper directoryNodeMapper;
    private final JwtUtil jwtUtil;

    private static final String TOKEN_PREFIX = "Bearer ";

    /**
     * 上传文档并自动处理（切割+向量化）
     *
     * @param parentNodeId 可选，上传到指定目录节点
     */
    @PostMapping("/upload")
    public ResponseEntity<Map<String, Object>> uploadDocument(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "parentNodeId", required = false) Long parentNodeId,
            @RequestParam(value = "kbId", required = false) Long kbId,
            HttpServletRequest httpRequest) {

        Long userId = getCurrentUserId(httpRequest);
        if (userId == null) {
            return ResponseEntity.status(401).build();
        }

        try {
            // 1. 上传文件
            String docId = documentService.uploadDocument(userId, file, kbId);

            // 2. 创建目录节点
            Long targetParentId = parentNodeId != null ? parentNodeId : null; // null = 根级
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

            // 3. 异步处理文档（切割+向量化）
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

    /**
     * 获取用户文档列表
     */
    @GetMapping("/list")
    public ResponseEntity<List<Document>> listDocuments(HttpServletRequest httpRequest) {
        Long userId = getCurrentUserId(httpRequest);
        if (userId == null) {
            return ResponseEntity.status(401).build();
        }

        List<Document> documents = documentService.getUserDocuments(userId);
        return ResponseEntity.ok(documents);
    }

    /**
     * 删除文档
     */
    @DeleteMapping("/{docId}")
    public ResponseEntity<Map<String, String>> deleteDocument(
            @PathVariable String docId,
            HttpServletRequest httpRequest) {

        Long userId = getCurrentUserId(httpRequest);
        if (userId == null) {
            return ResponseEntity.status(401).build();
        }

        boolean success = documentService.deleteDocument(docId, userId);
        if (success) {
            return ResponseEntity.ok(Map.of("message", "删除成功"));
        } else {
            return ResponseEntity.badRequest().body(Map.of("error", "删除失败"));
        }
    }

    /**
     * 获取文档内容（用于前端预览）
     */
    @GetMapping("/{docId}/content")
    public ResponseEntity<String> getDocumentContent(
            @PathVariable String docId,
            HttpServletRequest httpRequest) {

        Long userId = getCurrentUserId(httpRequest);
        if (userId == null) {
            return ResponseEntity.status(401).build();
        }

        Document document = documentService.getByDocId(docId);
        if (document == null) {
            return ResponseEntity.status(404).build();
        }
        // 允许：文档拥有者 or 共享文档访问者 or 共享知识库文档
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
            // 使用 FileStorageService（Apache Tika）解析，支持 txt/md/pdf/doc/docx/ppt/pptx
            String content = fileStorageService.readFileContent(document.getFilePath());
            return ResponseEntity.ok(content);
        } catch (Exception e) {
            log.warn("Failed to read document content: {}", docId, e);
            return ResponseEntity.status(500).body("无法读取文档内容");
        }
    }

    /**
     * 手动触发文档处理（用于重新处理）
     */
    @PostMapping("/{docId}/process")
    public ResponseEntity<Map<String, String>> processDocument(
            @PathVariable String docId,
            HttpServletRequest httpRequest) {

        Long userId = getCurrentUserId(httpRequest);
        if (userId == null) {
            return ResponseEntity.status(401).build();
        }

        Document document = documentService.getByDocId(docId);
        if (document == null || !document.getUserId().equals(userId)) {
            return ResponseEntity.status(403).body(Map.of("error", "无权访问该文档"));
        }

        documentService.processDocument(docId);

        return ResponseEntity.ok(Map.of("message", "文档处理已启动"));
    }

    /**
     * 获取目录树
     * kbId=null 获取个人树，有值获取共享知识库树
     */
    @GetMapping("/directory/tree")
    public ResponseEntity<List<DirectoryNode>> getDirectoryTree(
            @RequestParam(value = "kbId", required = false) Long kbId,
            HttpServletRequest httpRequest) {
        Long userId = getCurrentUserId(httpRequest);
        if (userId == null) return ResponseEntity.status(401).build();

        if (kbId != null) {
            return ResponseEntity.ok(documentService.getDirectoryTreeByKbId(kbId));
        }
        return ResponseEntity.ok(documentService.getDirectoryTree(userId));
    }

    /**
     * 创建文件夹
     */
    @PostMapping("/directory/folder")
    public ResponseEntity<Map<String, Object>> createFolder(
            @RequestBody Map<String, Object> body,
            HttpServletRequest httpRequest) {

        Long userId = getCurrentUserId(httpRequest);
        if (userId == null) {
            return ResponseEntity.status(401).build();
        }

        Long parentId = body.get("parentId") != null ? Long.valueOf(body.get("parentId").toString()) : null;
        String label = (String) body.get("label");
        Long kbId = body.get("kbId") != null ? Long.valueOf(body.get("kbId").toString()) : null;
        if (label == null || label.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "文件夹名称不能为空"));
        }

        Long nodeId = documentService.createFolder(userId, parentId, label.trim(), kbId);
        return ResponseEntity.ok(Map.of("id", nodeId, "message", "创建成功"));
    }

    /**
     * 重命名节点
     */
    @PutMapping("/directory/{id}/rename")
    public ResponseEntity<Map<String, String>> renameNode(
            @PathVariable Long id,
            @RequestBody Map<String, String> body,
            HttpServletRequest httpRequest) {

        Long userId = getCurrentUserId(httpRequest);
        if (userId == null) {
            return ResponseEntity.status(401).build();
        }

        String label = body.get("label");
        if (label == null || label.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "名称不能为空"));
        }

        documentService.renameNode(userId, id, label.trim());
        return ResponseEntity.ok(Map.of("message", "重命名成功"));
    }

    /**
     * 删除节点（递归删除子节点，file 类型同时删除关联文档）
     */
    @DeleteMapping("/directory/{id}")
    public ResponseEntity<Map<String, String>> deleteDirectoryNode(
            @PathVariable Long id,
            HttpServletRequest httpRequest) {

        Long userId = getCurrentUserId(httpRequest);
        if (userId == null) {
            return ResponseEntity.status(401).build();
        }

        documentService.deleteDirectoryNode(userId, id);
        return ResponseEntity.ok(Map.of("message", "删除成功"));
    }

    /**
     * 移动节点（拖拽）
     */
    @PutMapping("/directory/{id}/move")
    public ResponseEntity<Map<String, String>> moveNode(
            @PathVariable Long id,
            @RequestBody Map<String, Object> body,
            HttpServletRequest httpRequest) {

        Long userId = getCurrentUserId(httpRequest);
        if (userId == null) {
            return ResponseEntity.status(401).build();
        }

        Long targetParentId = body.get("targetParentId") != null
                ? Long.valueOf(body.get("targetParentId").toString()) : null;
        Integer sortOrder = body.get("sortOrder") != null
                ? Integer.valueOf(body.get("sortOrder").toString()) : null;

        documentService.moveNode(userId, id, targetParentId, sortOrder);
        return ResponseEntity.ok(Map.of("message", "移动成功"));
    }

    /**
     * 切换共享状态
     */
    @PutMapping("/directory/{id}/share")
    public ResponseEntity<Map<String, Object>> toggleShare(
            @PathVariable Long id,
            HttpServletRequest httpRequest) {

        Long userId = getCurrentUserId(httpRequest);
        if (userId == null) {
            return ResponseEntity.status(401).build();
        }

        boolean shared = documentService.toggleShare(userId, id);
        return ResponseEntity.ok(Map.of("shared", shared, "message", shared ? "已共享" : "已取消共享"));
    }

    /**
     * 获取其他用户共享的目录树
     */
    @SuppressWarnings("unchecked")
    @GetMapping("/directory/shared")
    public ResponseEntity<List<java.util.Map<String, Object>>> getSharedTree(HttpServletRequest httpRequest) {
        Long userId = getCurrentUserId(httpRequest);
        if (userId == null) {
            return ResponseEntity.status(401).build();
        }
        return ResponseEntity.ok(documentService.getSharedTree(userId));
    }

    /**
     * 从请求头获取当前用户ID
     */
    private Long getCurrentUserId(HttpServletRequest request) {
        String authHeader = request.getHeader("Authorization");
        if (authHeader == null || !authHeader.startsWith(TOKEN_PREFIX)) {
            return null;
        }

        String token = authHeader.substring(TOKEN_PREFIX.length());
        try {
            return jwtUtil.getUserIdFromToken(token);
        } catch (Exception e) {
            log.warn("解析 token 失败", e);
            return null;
        }
    }
}
