package com.firedemo.demo.Controller;

import com.firedemo.demo.agent.workflow.GradingWorkflow;
import com.firedemo.demo.agent.workflow.WorkflowEngine;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * Agent 增强接口
 * <p>
 * 暴露 Workflow 工作流端点。
 * 日常对话通过 OpenClaw Gateway 直接处理（含 MCP Tool Calling），
 * 复杂业务流程通过本 Controller 的工作流引擎编排。
 */
@Slf4j
@RestController
@RequestMapping("/api/agent")
@RequiredArgsConstructor
public class AgentController {

    private final GradingWorkflow gradingWorkflow;
    private final WorkflowEngine workflowEngine;

    // ==================== Workflow ====================

    /**
     * 执行作业批改工作流
     * <pre>
     * POST /api/agent/workflow/grading
     * {
     *   "submissionId": "123",
     *   "studentCode": "#include...",
     *   "requirement": "用 C 语言实现冒泡排序"
     * }
     * </pre>
     */
    @PostMapping("/workflow/grading")
    public Map<String, Object> runGradingWorkflow(@RequestBody Map<String, String> request) {
        Long submissionId = Long.parseLong(request.get("submissionId"));
        String studentCode = request.get("studentCode");
        String requirement = request.getOrDefault("requirement", "");

        log.info("启动批改工作流: submissionId={}", submissionId);

        //调用批改工作流的执行方法，返回 GradingState 工作流状态实体，该实体存储：流程实例 ID、批改结果、错误分析、建议、异常信息等。
        GradingWorkflow.GradingState state = gradingWorkflow.execute(
                submissionId, studentCode, requirement);

        return Map.of(
                "code", 200,
                "instanceId", state.getInstanceId(),
                "trace", workflowEngine.getTrace(state.getInstanceId()),
                "gradeResult", state.getStringAttr("gradeResult"),
                "errorAnalysis", state.getStringAttr("errorAnalysis"),
                "suggestion", state.getStringAttr("suggestion"),
                "error", state.getError() != null ? state.getError() : ""
        );
    }
}
