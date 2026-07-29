package com.firedemo.demo.Controller;

import com.firedemo.demo.DTO.*;
import com.firedemo.demo.Entity.ClassroomSession;
import com.firedemo.demo.Entity.Interaction;
import com.firedemo.demo.Entity.LiveConfusionEvent;
import com.firedemo.demo.Service.OpenClawService;
import com.firedemo.demo.common.exception.BusinessException;
import com.firedemo.demo.common.exception.ErrorCode;
import com.firedemo.demo.common.result.Result;
import com.firedemo.demo.live.service.InteractionService;
import com.firedemo.demo.live.service.LiveSessionService;
import com.firedemo.demo.live.service.ReportService;
import com.firedemo.demo.live.service.StudentPresenceService;
import com.firedemo.demo.live.security.ClassroomStudentPrincipal;
import com.firedemo.demo.live.security.StudentDeviceTokenService;
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
import org.springframework.http.HttpHeaders;
import jakarta.servlet.http.HttpServletResponse;

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
    private final OpenClawService openClawService;
    private final StudentDeviceTokenService studentDeviceTokenService;

    @PostMapping("/create")
    @PreAuthorize("@sec.isClassOwner(#dto.classId)")
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
    @PreAuthorize("@sec.isClassOwner(#classId)")
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
    public Result<LiveSessionInfoDTO> joinSession(@RequestBody LiveJoinDTO dto,
                                                  HttpServletResponse response) {
        if (dto.getCode() == null || dto.getCode().isBlank()) return Result.error(400, "请输入课堂码");
        if (dto.getStudentId() == null || dto.getStudentId().isBlank()) return Result.error(400, "请输入学号");
        LiveSessionInfoDTO info = completeStudentJoin(dto);
        bindStudentDevice(response, info.getStudentId());
        return Result.success(info);
    }

    /** 已绑定过身份的个人设备只需提交课堂码即可进入。 */
    @PostMapping("/quick-join")
    public Result<LiveSessionInfoDTO> quickJoin(
            @RequestBody LiveQuickJoinDTO dto,
            @CookieValue(name = StudentDeviceTokenService.COOKIE_NAME, required = false) String deviceToken,
            HttpServletResponse response) {
        if (dto.getCode() == null || dto.getCode().isBlank()) return Result.error(400, "请输入课堂码");
        String studentId = studentDeviceTokenService.parse(deviceToken)
                .orElseThrow(() -> new BusinessException(
                        ErrorCode.UNAUTHORIZED.getCode(),
                        "这台设备尚未绑定学生身份"));
        LiveJoinDTO join = new LiveJoinDTO();
        join.setCode(dto.getCode());
        join.setStudentId(studentId);
        LiveSessionInfoDTO info = completeStudentJoin(join);
        bindStudentDevice(response, info.getStudentId());
        return Result.success(info);
    }

    /** 清除当前设备身份，供借用设备或切换学生使用。 */
    @PostMapping("/device/unbind")
    public Result<Void> unbindStudentDevice(HttpServletResponse response) {
        response.addHeader(HttpHeaders.SET_COOKIE,
                studentDeviceTokenService.clearingCookie().toString());
        return Result.success();
    }

    @GetMapping("/session/{code}")
    public Result<LiveSessionInfoDTO> previewSession(@PathVariable String code) {
        return Result.success(sessionService.previewByCode(code.toUpperCase()));
    }

    private LiveSessionInfoDTO completeStudentJoin(LiveJoinDTO dto) {
        LiveSessionInfoDTO info = sessionService.joinSession(dto);
        info.setCurrentInteraction(interactionService.getActiveInteraction(info.getSessionId()));
        return info;
    }

    private void bindStudentDevice(HttpServletResponse response, String studentId) {
        response.addHeader(HttpHeaders.SET_COOKIE,
                studentDeviceTokenService.bindingCookie(studentId).toString());
    }

    @GetMapping("/session/{sessionId}/current-interaction")
    @PreAuthorize("@sec.canAccessLiveSession(#sessionId)")
    public Result<InteractionPushDTO> getCurrentInteraction(@PathVariable Long sessionId) {
        return Result.success(interactionService.getActiveInteraction(sessionId));
    }

    /** 获取当前互动的统计（老师刷新页面时兜底拉取） */
    @GetMapping("/session/{sessionId}/interaction-stats")
    @PreAuthorize("@sec.isSessionOwner(#sessionId)")
    public Result<LiveStatsDTO> getInteractionStats(@PathVariable Long sessionId) {
        return Result.success(interactionService.getActiveInteractionStats(sessionId));
    }

    /** 统一题库与本节课发送记录组成的控制看板。 */
    @GetMapping("/session/{sessionId}/question-board")
    @PreAuthorize("@sec.isSessionOwner(#sessionId)")
    public Result<List<QuestionBoardItemDTO>> getQuestionBoard(@PathVariable Long sessionId) {
        return Result.success(interactionService.getQuestionBoard(sessionId));
    }

    /** 从统一题库发送题目，本次课堂会创建独立的 Interaction 快照。 */
    @PostMapping("/session/{sessionId}/question/{questionId}/send")
    @PreAuthorize("@sec.isSessionOwner(#sessionId)")
    public Result<InteractionPushDTO> sendQuestion(
            @PathVariable Long sessionId,
            @PathVariable Long questionId,
            @RequestBody(required = false) Map<String, Object> body) {
        Long requestedTimeLimit = body != null ? toLong(body.get("timeLimit")) : null;
        if (requestedTimeLimit != null && requestedTimeLimit > Integer.MAX_VALUE) {
            return Result.error(400, "作答时间无效");
        }
        return Result.success(interactionService.sendQuestion(
                sessionId,
                questionId,
                requestedTimeLimit != null ? requestedTimeLimit.intValue() : null));
    }

    /** 给当前作答中的题目追加时间。 */
    @PostMapping("/session/{sessionId}/interaction/{interactionId}/extend")
    @PreAuthorize("@sec.isSessionOwner(#sessionId)")
    public Result<InteractionTimingDTO> extendInteraction(
            @PathVariable Long sessionId,
            @PathVariable Long interactionId,
            @RequestBody Map<String, Object> body) {
        Long seconds = toLong(body.get("seconds"));
        if (seconds == null || seconds > Integer.MAX_VALUE) {
            return Result.error(400, "延时时长无效");
        }
        return Result.success(interactionService.extendInteraction(sessionId, interactionId, seconds.intValue()));
    }

    /** AI 生成题目 */
    @PostMapping("/generate-question")
    @PreAuthorize("hasRole('TEACHER')")
    public Result<Map<String, Object>> generateQuestion(@RequestBody Map<String, String> body) {
        String topic = body.getOrDefault("topic", "");
        String type = body.getOrDefault("type", "CHOICE");
        if (topic.isBlank()) return Result.error(400, "请输入知识点");
        return Result.success(interactionService.generateQuestion(topic, type));
    }

    /** 获取单个互动详情（含分布 + 学生作答明细，老师点卡片展开） */
    @GetMapping("/session/{sessionId}/interaction/{interactionId}/detail")
    @PreAuthorize("@sec.isSessionOwner(#sessionId)")
    public Result<InteractionDetailDTO> getInteractionDetail(
            @PathVariable Long sessionId, @PathVariable Long interactionId) {
        return Result.success(interactionService.getInteractionDetail(sessionId, interactionId));
    }

    /** 获取全部互动历史（教师端看统计，学生端传 studentId 看自己作答） */
    @GetMapping("/session/{sessionId}/interactions")
    @PreAuthorize("@sec.canAccessLiveSession(#sessionId)")
    public Result<List<InteractionHistoryDTO>> getInteractionHistory(
            @PathVariable Long sessionId,
            @RequestParam(required = false) String studentId) {
        ClassroomStudentPrincipal student = getCurrentStudent();
        String scopedStudentId = student != null ? student.studentId() : studentId;
        return Result.success(interactionService.getInteractionHistory(sessionId, scopedStudentId));
    }

    /** 导出课程报告（HTML） */
    @GetMapping("/session/{sessionId}/report")
    @PreAuthorize("@sec.isSessionOwner(#sessionId)")
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
    @PreAuthorize("@sec.isClassOwner(#classId)")
    public Result<Map<String, Object>> getStudentProfile(
            @PathVariable String studentId, @RequestParam Long classId) {
        return Result.success(interactionService.getStudentProfile(studentId, classId));
    }

    @GetMapping("/session/{sessionId}/students")
    @PreAuthorize("@sec.isSessionOwner(#sessionId)")
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
     * 学生身份从课堂范围 Principal 中提取，忽略请求体中的身份字段。
     * <p>
     * 采用 RAG 检索 + LLM 生成的混合策略：
     * 1. RAG 检索知识库中相关文档作为参考上下文
     * 2. LLM 结合题目和上下文，用通俗语言生成解题思路讲解
     */
    @PostMapping("/confusion/mark")
    @PreAuthorize("hasRole('STUDENT')")
    public Result<Map<String, Object>> markConfusion(@RequestBody Map<String, Object> body) {
        ClassroomStudentPrincipal student = getCurrentStudent();
        if (student == null) {
            return Result.error(401, "请先加入课堂");
        }
        String studentId = student.studentId();
        String studentName = student.studentName();

        Long sessionId = toLong(body.get("sessionId"));
        Long interactionId = toLong(body.get("interactionId"));

        if (sessionId == null || studentId == null || studentId.isBlank()) {
            return Result.error(400, "sessionId 和登录凭证无效");
        }
        if (!student.liveSessionId().equals(sessionId)) {
            return Result.error(403, "无权访问该课堂");
        }

        // 去重：同一学生对同一题目只记录一次，重复请求直接返回已有解析
        if (interactionId != null) {
            LiveConfusionEvent existing = confusionEventMapper.findByInteractionAndStudent(interactionId, studentId);
            if (existing != null) {
                log.debug("不懂标记已存在，返回缓存: student={}, interactionId={}", studentId, interactionId);
                return Result.success(Map.of(
                        "knowledgePoint", existing.getKnowledgePoint(),
                        "explanation", existing.getAiExplanation()
                ));
            }
        }

        // 获取题目信息
        Interaction interaction = interactionId != null ? interactionMapper.selectById(interactionId) : null;
        String knowledgePoint = interaction != null ? interaction.getKnowledgePoint() : "未指定";
        String questionText = interaction != null ? interaction.getTitle() : "";
        String correctKey = interaction != null ? interaction.getCorrectKey() : null;
        String interactionType = interaction != null ? interaction.getType() : "";

        // Step 1: RAG 检索知识库上下文（作为 LLM 的参考资料）
        String ragContext = "";
        try {
            RagResult result = ragService.search(RagSearchRequest.builder()
                    .query(knowledgePoint)
                    .topK(3)
                    .enableReranker(true)
                    .format(RagSearchRequest.Format.FORMATTED_CONTENT)
                    .build());
            if (result.isHasContext()) {
                ragContext = result.getFormattedContent();
            }
        } catch (Exception e) {
            log.warn("RAG检索失败，将使用纯 LLM 解释", e);
        }

        // Step 2: LLM 生成学生友好的解题讲解
        String explanation;
        try {
            explanation = generateExplanation(knowledgePoint, questionText, correctKey, interactionType, ragContext);
        } catch (Exception e) {
            log.error("LLM生成解析失败", e);
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

    /**
     * 用 LLM 生成面向学生的解题思路讲解。
     * 有知识库上下文时作为参考，无上下文时依赖 LLM 自身知识。
     */
    private String generateExplanation(String knowledgePoint, String questionText,
                                       String correctKey, String type,
                                       String ragContext) {
        StringBuilder prompt = new StringBuilder();
        prompt.append("你是一位耐心、会讲课的老师。一个学生在课堂上遇到了下面这道题，标记了「不懂」，请帮他理清思路。\n\n");

        prompt.append("【知识点】").append(knowledgePoint).append("\n");
        prompt.append("【题目类型】").append("CHOICE".equals(type) ? "选择题" : "简答题").append("\n");
        prompt.append("【题目内容】").append(questionText).append("\n");
        if (correctKey != null && !correctKey.isBlank()) {
            prompt.append("【正确答案】").append(correctKey).append("\n");
        }

        if (!ragContext.isBlank()) {
            prompt.append("\n【知识库参考资料（仅供你参考，不要原文照搬）】\n").append(ragContext).append("\n");
        }

        prompt.append("\n请你：\n");
        prompt.append("1. 用通俗易懂的语言讲解这道题考察的核心知识点\n");
        prompt.append("2. 给出解题的关键步骤和思路（如果是选择题，解释为什么选这个答案）\n");
        prompt.append("3. 指出学生常见的误区或易错点\n\n");
        prompt.append("要求：\n");
        prompt.append("- 像老师在课下单独辅导学生一样，自然亲切\n");
        prompt.append("- 不要罗列知识库原文，用你自己的话组织\n");
        prompt.append("- 控制在300字以内，重点突出\n");
        prompt.append("- 如果知识库没有相关内容，就用你自己的知识来讲");

        String raw = openClawService.chat(prompt.toString(), "1");
        // 清理可能的 markdown 代码块包裹
        return cleanLlmResponse(raw);
    }

    /** 清理 LLM 响应中可能的 markdown 代码块包裹 */
    private String cleanLlmResponse(String raw) {
        if (raw == null) return "";
        String s = raw.trim();
        if (s.startsWith("```")) {
            int start = s.indexOf('\n');
            int end = s.lastIndexOf("```");
            if (start >= 0 && end > start) s = s.substring(start + 1, end);
            else if (start >= 0) s = s.substring(start + 1);
            else if (end > 3) s = s.substring(3, end);
        }
        return s.trim();
    }

    /** 教师端查看该课堂的不懂标记汇总（按知识点聚合） */
    @GetMapping("/session/{sessionId}/confusion-stats")
    @PreAuthorize("@sec.isSessionOwner(#sessionId)")
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
        } catch (Exception e) {
            log.debug("读取当前用户ID失败: {}", e.getMessage());
        }
        return null;
    }

    private String getCurrentRole() {
        try {
            var auth = SecurityContextHolder.getContext().getAuthentication();
            if (auth == null || !auth.isAuthenticated()) return null;
            return auth.getAuthorities().stream()
                    .map(org.springframework.security.core.GrantedAuthority::getAuthority)
                    .filter(a -> a.startsWith("ROLE_"))
                    .map(a -> a.substring("ROLE_".length()))
                    .findFirst()
                    .orElse(null);
        } catch (RuntimeException e) {
            log.debug("读取当前角色失败: {}", e.getMessage());
            return null;
        }
    }

    private ClassroomStudentPrincipal getCurrentStudent() {
        try {
            var auth = SecurityContextHolder.getContext().getAuthentication();
            return auth != null && auth.getPrincipal() instanceof ClassroomStudentPrincipal student
                    ? student
                    : null;
        } catch (RuntimeException e) {
            return null;
        }
    }
}
