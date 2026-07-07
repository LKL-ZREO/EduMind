package com.firedemo.demo.Controller;

import com.firedemo.demo.Entity.ClassInfo;
import com.firedemo.demo.Entity.ClassStudent;
import com.firedemo.demo.mapper.ClassInfoMapper;
import com.firedemo.demo.mapper.ClassStudentMapper;
import com.firedemo.demo.mapper.SharedKbMemberMapper;
import com.firedemo.demo.mapper.StudentQqBindingMapper;
import com.firedemo.demo.rag.RagResult;
import com.firedemo.demo.rag.RagSearchRequest;
import com.firedemo.demo.rag.RagService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.Set;

/**
 * OneBot RAG 接口 - 供 OpenClaw Gateway 调用
 * <p>
 * 委托 {@link RagService} 统一检索，返回增强后的消息。
 */
@Slf4j
@RestController
@RequestMapping("/api/onebot")
@RequiredArgsConstructor
public class OnebotRagController {

    private final RagService ragService;
    private final StudentQqBindingMapper studentQqBindingMapper;
    private final ClassStudentMapper classStudentMapper;
    private final ClassInfoMapper classInfoMapper;
    private final SharedKbMemberMapper sharedKbMemberMapper;

    /**
     * RAG 增强接口 - 委托 RagService 统一检索
     */
    @PostMapping("/rag")
    public ResponseEntity<RagResponse> rag(@RequestBody RagRequest request) {
        log.debug("RAG request from QQ: {}, message: {}", request.getQq(), request.getMessage());

        try {
            // 1. QQ → 学号 → 班级 → 老师 → 知识库权限
            Long userId = null;
            Set<Long> accessibleKbIds = null;
            Long courseId = null;
            try {
                String studentId = studentQqBindingMapper.selectStudentIdByQq(request.getQq());
                if (studentId != null) {
                    ClassStudent cs = classStudentMapper.selectByStudentId(studentId);
                    if (cs != null && cs.getClassId() != null) {
                        ClassInfo classInfo = classInfoMapper.selectById(cs.getClassId());
                        if (classInfo != null && classInfo.getTeacherId() != null) {
                            userId = classInfo.getTeacherId();
                            accessibleKbIds = sharedKbMemberMapper.selectKbIdsByUserId(userId);
                            courseId = classInfo.getCourseId();
                        }
                    }
                }
            } catch (Exception e) {
                log.debug("无法从QQ解析用户上下文，回退搜全库: qq={}", request.getQq(), e);
            }

            // 2. 委托 RagService
            RagSearchRequest searchRequest = RagSearchRequest.builder()
                    .query(request.getMessage())
                    .topK(3)
                    .userId(userId)
                    .accessibleKbIds(accessibleKbIds)
                    .courseId(courseId)
                    .enableReranker(true)
                    .format(RagSearchRequest.Format.ENHANCED_MESSAGE)
                    .build();

            RagResult result = ragService.search(searchRequest);

            return ResponseEntity.ok(new RagResponse(
                    result.getEnhancedMessage(), result.isHasContext()));

        } catch (Exception e) {
            log.error("RAG 增强失败，降级返回原消息", e);
            return ResponseEntity.ok(new RagResponse(request.getMessage(), false));
        }
    }

    /**
     * 健康检查
     */
    @GetMapping("/rag/health")
    public ResponseEntity<Map<String, String>> health() {
        boolean embeddingOk = false;
        try {
            // 通过 RagService 间接验证 embedding 可用
            RagResult result = ragService.search(RagSearchRequest.builder()
                    .query("test")
                    .topK(1)
                    .enableReranker(false)
                    .build());
            embeddingOk = true;
        } catch (Exception e) {
            log.debug("RAG 健康检查 embedding 失败: {}", e.getMessage());
        }
        return ResponseEntity.ok(Map.of(
                "status", embeddingOk ? "UP" : "DEGRADED",
                "embedding", String.valueOf(embeddingOk)
        ));
    }

    // ============ DTO ============

    @lombok.Data
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    public static class RagRequest {
        private String qq;
        private String message;
    }

    @lombok.Data
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    public static class RagResponse {
        private String enhancedMessage;
        private boolean hasContext;
    }
}
