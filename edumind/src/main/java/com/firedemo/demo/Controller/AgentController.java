package com.firedemo.demo.Controller;

import com.firedemo.demo.agent.workflow.GradingWorkflow;
import com.firedemo.demo.agent.workflow.WorkflowEngine;
import com.firedemo.demo.common.result.Result;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * Agent 增强接口 — 工作流引擎端点。
 */
@Slf4j
@RestController
@RequestMapping("/api/agent")
@RequiredArgsConstructor
public class AgentController {

    private final GradingWorkflow gradingWorkflow;
    private final WorkflowEngine workflowEngine;

    /**
     * 执行作业批改工作流（需登录）
     */
    @PostMapping("/workflow/grading")
    public Result<Map<String, Object>> runGradingWorkflow(@RequestBody Map<String, String> request) {
        Long userId = getCurrentUserId();
        if (userId == null) return Result.error(401, "未登录");

        Long submissionId = Long.parseLong(request.get("submissionId"));
        String studentCode = request.get("studentCode");
        String requirement = request.getOrDefault("requirement", "");

        log.info("启动批改工作流: submissionId={}, userId={}", submissionId, userId);

        GradingWorkflow.GradingState state = gradingWorkflow.execute(
                submissionId, studentCode, requirement);

        return Result.success(Map.of(
                "instanceId", state.getInstanceId(),
                "trace", workflowEngine.getTrace(state.getInstanceId()),
                "gradeResult", state.getStringAttr("gradeResult"),
                "errorAnalysis", state.getStringAttr("errorAnalysis"),
                "suggestion", state.getStringAttr("suggestion"),
                "error", state.getError() != null ? state.getError() : ""
        ));
    }

    private Long getCurrentUserId() {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || auth.getDetails() == null) return null;
        if (auth.getDetails() instanceof Long uid) return uid;
        return null;
    }
}
