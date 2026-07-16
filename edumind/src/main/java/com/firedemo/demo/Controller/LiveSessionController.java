package com.firedemo.demo.Controller;

import com.firedemo.demo.DTO.*;
import com.firedemo.demo.Entity.ClassroomSession;
import com.firedemo.demo.Entity.Interaction;
import com.firedemo.demo.Entity.LiveConfusionEvent;
import com.firedemo.demo.common.result.Result;
import com.firedemo.demo.live.service.InteractionService;
import com.firedemo.demo.live.service.LiveSessionService;
import com.firedemo.demo.live.service.ReportService;
import com.firedemo.demo.live.service.StudentPresenceService;
import com.firedemo.demo.mapper.InteractionMapper;
import com.firedemo.demo.mapper.LiveConfusionEventMapper;
import com.firedemo.demo.rag.RagResult;
import com.firedemo.demo.rag.RagSearchRequest;
import com.firedemo.demo.rag.RagService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/live")
@RequiredArgsConstructor
public class LiveSessionController {

    private final LiveSessionService sessionService;
    private final InteractionService interactionService;
    private final StudentPresenceService presenceService;
    private final ReportService reportService;
    private final RagService ragService;
    private final LiveConfusionEventMapper confusionEventMapper;
    private final InteractionMapper interactionMapper;

    @PostMapping("/create")
    public Result<Map<String, Object>> createSession(@RequestBody LiveSessionCreateDTO dto) {
        Long userId = getCurrentUserId();
        if (userId == null) return Result.error(401, "未登录");
        ClassroomSession session = sessionService.createSession(userId, dto);
        return Result.success(Map.of("sessionId", session.getId(), "sessionCode", session.getSessionCode(),
                "title", session.getTitle(), "classId", session.getClassId(), "status", session.getStatus(),
                "startedAt", session.getStartedAt() != null ? session.getStartedAt().toString() : ""));
    }

    @PostMapping("/end/{sessionId}")
    @PreAuthorize("@sec.isSessionOwner(#sessionId)")
    public Result<Void> endSession(@PathVariable Long sessionId) {
        Long userId = getCurrentUserId();
        sessionService.endSession(sessionId, userId);
        return Result.success(null);
    }

    @GetMapping("/active")
    public Result<Map<String, Object>> getActiveSession(@RequestParam Long classId) {
        Long userId = getCurrentUserId();
        if (userId == null) return Result.error(401, "未登录");
        ClassroomSession session = sessionService.findActiveByClassId(classId);
        if (session == null) return Result.success(Map.of("hasActive", false));
        return Result.success(Map.of("hasActive", true, "sessionId", session.getId(),
                "sessionCode", session.getSessionCode(), "title", session.getTitle(), "status", session.getStatus(),
                "startedAt", session.getStartedAt() != null ? session.getStartedAt().toString() : ""));
    }

    @PostMapping("/join")
    public Result<LiveSessionInfoDTO> joinSession(@RequestBody LiveJoinDTO dto) {
        if (dto.getCode() == null || dto.getCode().isBlank()) return Result.error(400, "请输入课堂码");
        if (dto.getStudentId() == null || dto.getStudentId().isBlank()) return Result.error(400, "请输入学号");
        return Result.success(sessionService.joinSession(dto));
    }

    @GetMapping("/session/{code}")
    public Result<LiveSessionInfoDTO> previewSession(@PathVariable String code) {
        return Result.success(sessionService.previewByCode(code.toUpperCase()));
    }

    @GetMapping("/session/{sessionId}/current-interaction")
    public Result<InteractionPushDTO> getCurrentInteraction(@PathVariable Long sessionId) {
        return Result.success(interactionService.getActiveInteraction(sessionId));
    }

    /** 获取当前互动的统计（老师刷新页面时兜底拉取） */
    @GetMapping("/session/{sessionId}/interaction-stats")
    public Result<LiveStatsDTO> getInteractionStats(@PathVariable Long sessionId) {
        return Result.success(interactionService.getActiveInteractionStats(sessionId));
    }

    /** AI 生成题目 */
    @PostMapping("/generate-question")
    public Result<Map<String, Object>> generateQuestion(@RequestBody Map<String, String> body) {
        String topic = body.getOrDefault("topic", "");
        String type = body.getOrDefault("type", "CHOICE");
        if (topic.isBlank()) return Result.error(400, "请输入知识点");
        return Result.success(interactionService.generateQuestion(topic, type));
    }

    /** 获取单个互动详情（含分布 + 学生作答明细，老师点卡片展开） */
    @GetMapping("/session/{sessionId}/interaction/{interactionId}/detail")
    public Result<InteractionDetailDTO> getInteractionDetail(
            @PathVariable Long sessionId, @PathVariable Long interactionId) {
        return Result.success(interactionService.getInteractionDetail(sessionId, interactionId));
    }

    /** 获取全部互动历史（教师端看统计，学生端传 studentId 看自己作答） */
    @GetMapping("/session/{sessionId}/interactions")
    public Result<List<InteractionHistoryDTO>> getInteractionHistory(
            @PathVariable Long sessionId,
            @RequestParam(required = false) String studentId) {
        return Result.success(interactionService.getInteractionHistory(sessionId, studentId));
    }

    /** 导出课程报告（HTML） */
    @GetMapping("/session/{sessionId}/report")
    public Result<Map<String, String>> getReport(
            @PathVariable Long sessionId,
            @RequestParam(defaultValue = "") String title,
            @RequestParam(defaultValue = "") String duration,
            @RequestParam(defaultValue = "0") int online,
            @RequestParam(defaultValue = "0") int absent,
            @RequestParam(defaultValue = "0") int qa) {
        String html = reportService.generateHtml(sessionId, title, duration, online, absent, qa);
        return Result.success(Map.of("html", html));
    }

    /** 学生个人画像 */
    @GetMapping("/student/{studentId}/profile")
    public Result<Map<String, Object>> getStudentProfile(
            @PathVariable String studentId, @RequestParam Long classId) {
        return Result.success(interactionService.getStudentProfile(studentId, classId));
    }

    @GetMapping("/session/{sessionId}/students")
    public Result<Map<String, Object>> getOnlineStudents(@PathVariable Long sessionId) {
        var online = presenceService.getOnlineStudents(sessionId);
        var absent = presenceService.getAbsentStudents(sessionId);
        return Result.success(Map.of(
                "count", online.size(),
                "students", online,
                "absentCount", absent.size(),
                "absentStudents", absent));
    }

    // ======================== 课堂"不懂"标记 ========================

    /**
     * 学生对课堂推送到题目标记"不懂"，AI 即时返回该知识点的解析。
     */
    @PostMapping("/confusion/mark")
    public Result<Map<String, Object>> markConfusion(@RequestBody Map<String, Object> body) {
        Long sessionId = toLong(body.get("sessionId"));
        Long interactionId = toLong(body.get("interactionId"));
        String studentId = (String) body.get("studentId");
        String studentName = (String) body.get("studentName");

        if (sessionId == null || studentId == null || studentId.isBlank()) {
            return Result.error(400, "sessionId 和 studentId 必填");
        }

        // 获取题目信息
        Interaction interaction = interactionId != null ? interactionMapper.selectById(interactionId) : null;
        String knowledgePoint = interaction != null ? interaction.getKnowledgePoint() : "未指定";
        String questionText = interaction != null ? interaction.getTitle() : "";

        // RAG 检索该知识点的解析
        String explanation;
        try {
            RagResult result = ragService.search(RagSearchRequest.builder()
                    .query(knowledgePoint)
                    .topK(3)
                    .enableReranker(true)
                    .format(RagSearchRequest.Format.FORMATTED_CONTENT)
                    .build());
            explanation = result.isHasContext()
                    ? "📚 **" + knowledgePoint + "** 解析：\n\n" + result.getFormattedContent()
                    : "📚 关于「" + knowledgePoint + "」的解析暂未找到，老师课后会统一讲解。";
        } catch (Exception e) {
            log.warn("RAG检索失败，降级", e);
            explanation = "解析生成失败，请稍后重试。";
        }

        // 记录事件
        confusionEventMapper.insert(LiveConfusionEvent.builder()
                .sessionId(sessionId)
                .interactionId(interactionId)
                .studentId(studentId)
                .studentName(studentName)
                .knowledgePoint(knowledgePoint)
                .questionText(questionText)
                .aiExplanation(explanation)
                .build());

        log.info("课堂不懂标记: student={}, kp={}, sessionId={}", studentId, knowledgePoint, sessionId);
        return Result.success(Map.of(
                "knowledgePoint", knowledgePoint,
                "explanation", explanation
        ));
    }

    /** 教师端查看该课堂的不懂标记汇总（按知识点聚合） */
    @GetMapping("/session/{sessionId}/confusion-stats")
    public Result<Map<String, Object>> getConfusionStats(@PathVariable Long sessionId) {
        List<Map<String, Object>> stats = confusionEventMapper.countByKnowledgePoint(sessionId);
        List<LiveConfusionEvent> events = confusionEventMapper.findBySessionId(sessionId);
        return Result.success(Map.of(
                "stats", stats,
                "total", events.size(),
                "events", events
        ));
    }

    private static Long toLong(Object v) {
        if (v instanceof Number n) return n.longValue();
        if (v instanceof String s) {
            try { return Long.parseLong(s); } catch (NumberFormatException ignored) {}
        }
        return null;
    }

    private Long getCurrentUserId() {
        try {
            Object details = SecurityContextHolder.getContext().getAuthentication().getDetails();
            if (details instanceof Long id) return id;
            if (details instanceof Integer i) return i.longValue();
        } catch (Exception ignored) {}
        return null;
    }
}
