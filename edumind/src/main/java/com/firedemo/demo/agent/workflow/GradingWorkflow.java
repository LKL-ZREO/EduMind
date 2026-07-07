package com.firedemo.demo.agent.workflow;

import com.firedemo.demo.Service.OpenClawService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 作业批改工作流 — 将批改流程拆为 4 个 DAG 节点
 * <pre>
 *   [加载作业] → [AI 批改打分] → [错题分析] → [生成建议]
 *                                   ↓(有薄弱点)
 *                              [知识库检索]
 * </pre>
 * <p>
 * 面试价值：展示你对 Workflow 的理解，不是简单的一次 LLM 调用，
 * 而是将复杂任务分解为多个可组合、可重试的步骤。
 */
@Slf4j
@Component
public class GradingWorkflow {

    private final OpenClawService openClawService;
    private final WorkflowEngine engine;

    public GradingWorkflow(OpenClawService openClawService, WorkflowEngine engine) {
        this.openClawService = openClawService;
        this.engine = engine;
    }

    /**
     * 构建并返回作业批改工作流定义
     */
    public WorkflowDefinition<GradingState> buildDefinition() {
        WorkflowDefinition<GradingState> wf = new WorkflowDefinition<>();
        wf.setName("作业批改工作流");
        wf.setDescription("分析代码 → 评分 → 错题诊断 → 学习建议");
        wf.setEntryNode("LOAD");

        // 节点 1: 加载作业
        wf.addNode(WorkflowNode.of(
                "LOAD", "加载作业内容",
                state -> {
                    log.info("Step 1: 加载作业 submissionId={}", state.getSubmissionId());
                    state.setStep1Done(true);
                    return "loaded";
                }
        ));

        // 节点 2: AI 批改打分
        wf.addNode(WorkflowNode.of(
                "GRADE", "AI 批改评分",
                state -> {
                    log.info("Step 2: AI 批改");
                    String prompt = buildGradingPrompt(state);
                    String result = openClawService.chat(prompt, state.getInstanceId());
                    state.setGradeResult(result);
                    return "graded";
                },
                "ERROR_HANDLER"  // 失败时跳转错误处理
        ));

        // 节点 3: 错题分析（仅当有错误时进入）
        wf.addNode(WorkflowNode.of(
                "ERROR_ANALYSIS", "薄弱知识点分析",
                state -> {
                    log.info("Step 3: 错题分析");
                    String prompt = "根据以下批改结果，分析学生的薄弱知识点：\n"
                            + state.getGradeResult();
                    String result = openClawService.chat(prompt, state.getInstanceId());
                    state.setErrorAnalysis(result);
                    return "analyzed";
                }
        ));

        // 节点 4: 生成学习建议
        wf.addNode(WorkflowNode.of(
                "SUGGESTION", "生成学习建议",
                state -> {
                    log.info("Step 4: 生成建议");
                    String prompt = "根据以下批改和错题分析，生成针对性的学习改进建议：\n"
                            + "批改结果：" + state.getGradeResult() + "\n"
                            + "薄弱点：" + state.getErrorAnalysis();
                    String result = openClawService.chat(prompt, state.getInstanceId());
                    state.setSuggestion(result);
                    state.setCompleted(true);
                    return "done";
                }
        ));

        // 错误处理节点
        wf.addNode(WorkflowNode.of(
                "ERROR_HANDLER", "批改失败时的兜底处理",
                state -> {
                    log.warn("批改失败，进入兜底: {}", state.getLastError());
                    state.setGradeResult("{\"error\": \"" + state.getLastError() + "\"}");
                    state.setCompleted(true);
                    return "fallback";
                }
        ));

        // 定义边（转移条件）
        wf.addEdge("LOAD", "GRADE");                                              // 无条件
        wf.addEdge("GRADE", "ERROR_ANALYSIS", s -> hasErrors(s) ? "ERROR_ANALYSIS" : null);
        wf.addEdge("GRADE", "SUGGESTION", s -> !hasErrors(s) ? "SUGGESTION" : null);
        wf.addEdge("ERROR_ANALYSIS", "SUGGESTION");                               // 无条件
        // ERROR_HANDLER 无出边 = 终点

        return wf;
    }

    /**
     * 执行批改工作流
     */
    public GradingState execute(Long submissionId, String studentCode, String requirement) {
        GradingState state = GradingState.create(submissionId, studentCode, requirement);
        state = engine.execute(buildDefinition(), state);

        log.info("批改工作流完成: instanceId={}, 执行轨迹={}",
                state.getInstanceId(), engine.getTrace(state.getInstanceId()));

        return state;
    }

    // ==================== 私有方法 ====================

    private String buildGradingPrompt(GradingState state) {
        return String.format("""
                你是一名资深编程老师，请批改以下学生作业。
                
                作业要求：%s
                
                学生代码：
                %s
                
                请严格按 JSON 格式输出：
                {
                  "totalScore": 整数(0-100),
                  "summary": "总体评价",
                  "errors": [{ "issue": "问题描述", "type": "error/warning", "severity": "major/minor", "knowledgePoint": "相关知识点" }],
                  "suggestions": [{ "issue": "问题", "suggestion": "建议", "priority": "high/medium/low", "knowledgePoint": "相关知识点" }]
                }
                """, state.getRequirement(), state.getStudentCode());
    }

    private boolean hasErrors(GradingState state) {
        String gradeResult = state.getGradeResult();
        return gradeResult != null && gradeResult.contains("\"errors\"")
                && !gradeResult.contains("\"errors\": []");
    }

    // ==================== 工作流状态 ====================

    @lombok.Data
    @lombok.EqualsAndHashCode(callSuper = true)
    public static class GradingState extends WorkflowState {

        private Long submissionId;
        private String studentCode;
        private String requirement;

        // ── 类型安全的工作流产物（替代 setAttr/getStringAttr） ──
        private String gradeResult;
        private String errorAnalysis;
        private String suggestion;
        private boolean step1Done;

        /** 节点失败时的错误信息（由 WorkflowEngine 写入） */
        public String getLastError() {
            return getStringAttr("lastError");
        }

        public static GradingState create(Long submissionId, String studentCode, String requirement) {
            GradingState s = new GradingState();
            s.setInstanceId("grading-" + submissionId + "-" + System.currentTimeMillis());
            s.submissionId = submissionId;
            s.studentCode = studentCode;
            s.requirement = requirement;
            s.setStartTime(java.time.LocalDateTime.now());
            return s;
        }
    }
}
