package com.firedemo.demo.Controller;

import com.firedemo.demo.Entity.Document;
import com.firedemo.demo.Service.DocumentService;
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
    private final JwtUtil jwtUtil;

    private static final String TOKEN_PREFIX = "Bearer ";

    /**
     * 上传文档并自动处理（切割+向量化）
     */
    @PostMapping("/upload")
    public ResponseEntity<Map<String, Object>> uploadDocument(
            @RequestParam("file") MultipartFile file,
            HttpServletRequest httpRequest) {

        Long userId = getCurrentUserId(httpRequest);
        if (userId == null) {
            return ResponseEntity.status(401).build();
        }

        try {
            // 1. 上传文件
            String docId = documentService.uploadDocument(userId, file);

            // 2. 异步处理文档（切割+向量化）
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
