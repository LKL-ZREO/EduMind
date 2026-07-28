package com.firedemo.demo.Controller;

import com.firedemo.demo.DTO.GenerateMaterialsRequest;
import com.firedemo.demo.DTO.GenerateMaterialsResponse;
import com.firedemo.demo.Entity.DirectoryNode;
import com.firedemo.demo.Entity.Document;
import com.firedemo.demo.Service.DocumentService;
import com.firedemo.demo.Service.FileStorageService;
import com.firedemo.demo.common.exception.BusinessException;
import com.firedemo.demo.common.exception.ErrorCode;
import com.firedemo.demo.config.OwnershipGuard;
import com.firedemo.demo.live.service.TeachingMaterialGenerator;
import com.firedemo.demo.mapper.DirectoryNodeMapper;
import com.firedemo.demo.mapper.InteractionMapper;
import com.firedemo.demo.mapper.PreviewTaskMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
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
    private final TeachingMaterialGenerator materialGenerator;
    private final PreviewTaskMapper previewTaskMapper;
    private final InteractionMapper interactionMapper;
    private final ObjectMapper objectMapper;
    private final OwnershipGuard ownershipGuard;

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
            Long targetParentId = parentNodeId != null ? parentNodeId : null;
            requireAccess(kbId == null || ownershipGuard.canAccessKnowledgeBase(userId, kbId));
            if (parentNodeId != null) {
                DirectoryNode parent = directoryNodeMapper.selectById(parentNodeId);
                if (parent == null || !ownershipGuard.canAccessDirectoryNode(userId, parentNodeId)) {
                    throw new IllegalArgumentException("目标目录不存在或无权访问");
                }
                if (!Objects.equals(parent.getKbId(), kbId))
                    throw new IllegalArgumentException("目标目录与知识库不匹配");
                if (!"folder".equals(parent.getNodeType())) {
                    throw new IllegalArgumentException("只能上传到文件夹中");
                }
            }

            String docId = documentService.uploadDocument(userId, file, kbId);

            int maxOrder = parentNodeId != null
                ? Optional.ofNullable(directoryNodeMapper.selectByParentId(parentNodeId))
                    .map(list -> list.stream().mapToInt(DirectoryNode::getSortOrder).max().orElse(-1))
                    .orElse(-1)
                : Optional.ofNullable(kbId == null
                        ? directoryNodeMapper.selectByUserId(userId)
                        : directoryNodeMapper.selectByKbId(kbId))
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

        } catch (BusinessException e) {
            throw e;
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
    @PreAuthorize("@sec.canWriteDocument(#docId)")
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

        requireAccess(ownershipGuard.canReadDocument(userId, docId));

        try {
            String content = fileStorageService.readFileContent(document.getFilePath());
            return ResponseEntity.ok(content);
        } catch (Exception e) {
            log.warn("Failed to read document content: {}", docId, e);
            return ResponseEntity.status(500).body("无法读取文档内容");
        }
    }

    @PostMapping("/{docId}/process")
    @PreAuthorize("@sec.canWriteDocument(#docId)")
    public ResponseEntity<Map<String, String>> processDocument(@PathVariable String docId) {
        documentService.processDocument(docId);
        return ResponseEntity.ok(Map.of("message", "文档处理已启动"));
    }

    @GetMapping("/directory/tree")
    public ResponseEntity<List<DirectoryNode>> getDirectoryTree(
            @RequestParam(value = "kbId", required = false) Long kbId) {
        Long userId = getCurrentUserId();
        if (userId == null) return ResponseEntity.status(401).build();
        if (kbId != null) return ResponseEntity.ok(documentService.getDirectoryTreeByKbId(userId, kbId));
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
    @PreAuthorize("@sec.canAccessDirectoryNode(#id)")
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
    @PreAuthorize("@sec.canAccessDirectoryNode(#id)")
    public ResponseEntity<Map<String, String>> deleteDirectoryNode(@PathVariable Long id) {
        Long userId = getCurrentUserId();
        documentService.deleteDirectoryNode(userId, id);
        return ResponseEntity.ok(Map.of("message", "删除成功"));
    }

    @PutMapping("/directory/{id}/move")
    @PreAuthorize("@sec.canAccessDirectoryNode(#id)")
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

    // ========== AI 生成教学材料 ==========

    @PostMapping("/generate-materials")
    public ResponseEntity<GenerateMaterialsResponse> generateMaterials(
            @RequestBody GenerateMaterialsRequest req) {
        Long userId = getCurrentUserId();
        if (userId == null) return ResponseEntity.status(401).build();
        requireAccess(ownershipGuard.canReadDocument(userId, req.getDocId()));
        if (req.getClassId() != null) requireAccess(ownershipGuard.isClassOwner(req.getClassId()));
        req.setTeacherId(userId);
        GenerateMaterialsResponse result = materialGenerator.generate(req, userId);
        return ResponseEntity.ok(result);
    }

    /** 保存已审核的预习作业 */
    @PostMapping("/generate-materials/save-preview")
    public ResponseEntity<Map<String, Object>> savePreview(
            @RequestBody Map<String, Object> body) {
        Long userId = getCurrentUserId();
        if (userId == null) return ResponseEntity.status(401).build();

        GenerateMaterialsResponse.PreviewItem item = GenerateMaterialsResponse.PreviewItem.builder()
                .topic((String) body.get("topic"))
                .guideText((String) body.get("guideText"))
                .discussionQuestion((String) body.get("discussionQuestion"))
                .build();
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> questionsRaw = (List<Map<String, Object>>) body.get("questions");

        Long classId = body.get("classId") instanceof Number n ? n.longValue() : null;
        if (classId == null) return ResponseEntity.badRequest().body(Map.of("error", "请选择班级"));
        requireAccess(ownershipGuard.isClassOwner(classId));

        String docId = (String) body.get("docId");
        if (docId != null) requireAccess(ownershipGuard.canReadDocument(userId, docId));
        GenerateMaterialsResponse.PreviewItem saved = materialGenerator.savePreview(
                item, classId, userId, docId);
        return ResponseEntity.ok(Map.of("savedId", saved.getSavedId()));
    }

    /** 保存已审核的课堂试题 */
    /** 获取某文档生成的教学材料草稿 */
    @GetMapping("/drafts")
    public ResponseEntity<Map<String, Object>> listDrafts(@RequestParam String docId) {
        Long userId = getCurrentUserId();
        if (userId == null) return ResponseEntity.status(401).build();
        requireAccess(ownershipGuard.canReadDocument(userId, docId));

        List<Map<String, Object>> previews = previewTaskMapper.findBySourceDocId(docId).stream()
                .map(p -> Map.<String, Object>of(
                        "id", p.getId(), "type", "preview", "title", p.getTitle(),
                        "topic", p.getKnowledgePoint() != null ? p.getKnowledgePoint() : "",
                        "status", p.getStatus(), "createdAt", p.getCreatedAt() != null ? p.getCreatedAt().toString() : ""))
                .toList();

        List<Map<String, Object>> quizzes = interactionMapper.findDraftsByDocId(docId).stream()
                .map(i -> {
                    Map<String, Object> m = new HashMap<>();
                    m.put("id", i.getId());
                    m.put("type", "quiz");
                    m.put("quizType", i.getType());
                    m.put("title", i.getTitle());
                    m.put("knowledgePoint", i.getKnowledgePoint() != null ? i.getKnowledgePoint() : "");
                    m.put("correctKey", i.getCorrectKey() != null ? i.getCorrectKey() : "");
                    m.put("timeLimit", i.getTimeLimit() != null ? i.getTimeLimit() : 0);
                    m.put("status", i.getStatus());
                    m.put("createdAt", i.getCreatedAt() != null ? i.getCreatedAt().toString() : "");
                    // 解析 options JSON 字符串为对象列表
                    if (i.getOptions() != null && !i.getOptions().isEmpty()) {
                        try {
                            m.put("options", objectMapper.readValue(i.getOptions(), List.class));
                        } catch (Exception e) {
                            m.put("options", List.of());
                        }
                    } else {
                        m.put("options", List.of());
                    }
                    return m;
                })
                .toList();

        return ResponseEntity.ok(Map.of("previews", previews, "quizzes", quizzes));
    }

    /** 删除草稿 */
    @DeleteMapping("/drafts/{type}/{id}")
    public ResponseEntity<Map<String, String>> deleteDraft(@PathVariable String type, @PathVariable Long id) {
        Long userId = getCurrentUserId();
        if (userId == null) return ResponseEntity.status(401).build();
        if ("preview".equals(type)) {
            requireAccess(ownershipGuard.isPreviewTaskOwner(id));
            previewTaskMapper.deleteById(id);
        } else if ("quiz".equals(type)) {
            requireAccess(ownershipGuard.isInteractionDraftOwner(id));
            interactionMapper.deleteDraft(id);
        } else {
            return ResponseEntity.badRequest().body(Map.of("error", "不支持的草稿类型"));
        }
        return ResponseEntity.ok(Map.of("message", "已删除"));
    }

    @PostMapping("/generate-materials/save-quiz")
    public ResponseEntity<Map<String, Object>> saveQuiz(
            @RequestBody Map<String, Object> body) {
        Long userId = getCurrentUserId();
        if (userId == null) return ResponseEntity.status(401).build();

        Long classId = body.get("classId") instanceof Number n ? n.longValue() : null;
        if (classId == null) return ResponseEntity.badRequest().body(Map.of("error", "请选择班级"));
        requireAccess(ownershipGuard.isClassOwner(classId));

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> optionsRaw = (List<Map<String, Object>>) body.get("options");
        List<GenerateMaterialsResponse.OptionItem> options = null;
        if (optionsRaw != null) {
            options = optionsRaw.stream().map(m -> GenerateMaterialsResponse.OptionItem.builder()
                    .key((String) m.get("key")).text((String) m.get("text")).build()).toList();
        }

        GenerateMaterialsResponse.QuizItem item = GenerateMaterialsResponse.QuizItem.builder()
                .type((String) body.get("type"))
                .title((String) body.get("title"))
                .options(options)
                .correctKey((String) body.get("correctKey"))
                .knowledgePoint((String) body.get("knowledgePoint"))
                .difficulty((String) body.get("difficulty"))
                .timeLimit(body.get("timeLimit") instanceof Number n ? n.intValue() : null)
                .build();
        String docId = (String) body.get("docId");
        if (docId != null) requireAccess(ownershipGuard.canReadDocument(userId, docId));
        GenerateMaterialsResponse.QuizItem saved = materialGenerator.saveQuiz(item, classId, docId, userId);
        return ResponseEntity.ok(Map.of("savedId", saved.getSavedId()));
    }

    // ========== 内部工具 ==========

    private Long getCurrentUserId() {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || auth.getDetails() == null) return null;
        if (auth.getDetails() instanceof Long uid) return uid;
        return null;
    }

    private void requireAccess(boolean allowed) {
        if (!allowed) throw new BusinessException(ErrorCode.FORBIDDEN);
    }
}
