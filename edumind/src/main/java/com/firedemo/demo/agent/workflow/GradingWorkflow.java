package com.firedemo.demo.agent.workflow;

import com.firedemo.demo.DTO.EvaluationResultDTO;
import com.firedemo.demo.Entity.Submission;
import com.firedemo.demo.Service.OpenClawService;
import com.firedemo.demo.infrastructure.ai.StructuredOutputInvoker;
import com.firedemo.demo.infrastructure.ai.StructuredOutputValidationException;
import com.firedemo.demo.infrastructure.prompt.PromptLoader;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 作业批改工作流 — 将批改流程拆为 3 个 DAG 节点，嵌入 GradingStreamConsumer
 * <pre>
 *   [GRADE] → [ERROR_ANALYSIS] → [SUGGESTION]
 *      ↓ 失败                      ↓ 失败
 *     兜底评分                   跳过，继续
 * </pre>
 * <p>
 * 每个节点内部使用 {@link StructuredOutputInvoker} 实现 JSON 解析失败自动重试。
 * 节点异常不影响后续步骤——GRADE 失败用兜底评分，ERROR/SUGGEST 失败跳过。
 */
@Slf4j
@Component
public class GradingWorkflow {

    private final OpenClawService openClawService;
    private final WorkflowEngine engine;
    private final StructuredOutputInvoker structuredOutputInvoker;
    private final PromptLoader promptLoader;

    public GradingWorkflow(OpenClawService openClawService,
                           WorkflowEngine engine,
                           StructuredOutputInvoker structuredOutputInvoker,
                           PromptLoader promptLoader) {
        this.openClawService = openClawService;
        this.engine = engine;
        this.structuredOutputInvoker = structuredOutputInvoker;
        this.promptLoader = promptLoader;
    }

    /**
     * 构建作业批改工作流定义
     */
    public WorkflowDefinition<GradingState> buildDefinition() {
        WorkflowDefinition<GradingState> wf = new WorkflowDefinition<>();
        wf.setName("作业批改工作流");
        wf.setDescription("评分 → 错题诊断 → 学习建议");
        wf.setEntryNode("GRADE");

        // ── 节点 1: 评分 ──
        wf.addNode(WorkflowNode.of(
                "GRADE", "AI 批改评分",
                state -> {
                    log.info("DAG-GRADE: submissionId={}", state.getSubmissionId());
                    try {
                        String prompt = buildGradePrompt(state);
                        EvaluationResultDTO result = structuredOutputInvoker.invoke(
                                p -> openClawService.chat(p, "grading_grade_" + state.getSubmissionId()),
                                prompt,
                                EvaluationResultDTO.class,
                                "DAG-GRADE submissionId=" + state.getSubmissionId(),
                                this::validateGradeResult);
                        state.setGradeResult(result);
                        return "graded";
                    } catch (Exception e) {
                        log.warn("DAG-GRADE 失败，使用兜底评分: submissionId={}, error={}",
                                state.getSubmissionId(), e.getMessage());
                        EvaluationResultDTO fallback = new EvaluationResultDTO();
                        fallback.setTotalScore(0);
                        fallback.setContentScore(0);
                        fallback.setFormatScore(0);
                        fallback.setMaxScore(100);
                        fallback.setOverallComment("批改失败：" + e.getMessage());
                        fallback.setStrengths(Collections.emptyList());
                        fallback.setWeaknesses(List.of("系统自动批改异常，请联系老师人工评阅"));
                        state.setGradeResult(fallback);
                        return "graded_fallback";
                    }
                }
        ));

        // ── 节点 2: 错题分析 ──
        wf.addNode(WorkflowNode.of(
                "ERROR_ANALYSIS", "错题分析与知识点归类",
                state -> {
                    log.info("DAG-ERROR_ANALYSIS: submissionId={}", state.getSubmissionId());
                    try {
                        String prompt = buildErrorAnalysisPrompt(state);
                        EvaluationResultDTO result = structuredOutputInvoker.invoke(
                                p -> openClawService.chat(p, "grading_error_" + state.getSubmissionId()),
                                prompt,
                                EvaluationResultDTO.class,
                                "DAG-ERROR submissionId=" + state.getSubmissionId(),
                                output -> requireList("errors", output.getErrors()));
                        state.setErrors(result.getErrors() != null
                                ? result.getErrors() : Collections.emptyList());
                        return "analyzed";
                    } catch (Exception e) {
                        log.warn("DAG-ERROR_ANALYSIS 失败，跳过: submissionId={}, error={}",
                                state.getSubmissionId(), e.getMessage());
                        state.setErrors(Collections.emptyList());
                        return "analyzed_skipped";
                    }
                }
        ));

        // ── 节点 3: 学习建议 ──
        wf.addNode(WorkflowNode.of(
                "SUGGESTION", "生成学习改进建议",
                state -> {
                    log.info("DAG-SUGGESTION: submissionId={}", state.getSubmissionId());
                    try {
                        String prompt = buildSuggestionPrompt(state);
                        EvaluationResultDTO result = structuredOutputInvoker.invoke(
                                p -> openClawService.chat(p, "grading_sug_" + state.getSubmissionId()),
                                prompt,
                                EvaluationResultDTO.class,
                                "DAG-SUGGEST submissionId=" + state.getSubmissionId(),
                                output -> requireList("suggestions", output.getSuggestions()));
                        state.setSuggestions(result.getSuggestions() != null
                                ? result.getSuggestions() : Collections.emptyList());
                        state.setCompleted(true);
                        return "done";
                    } catch (Exception e) {
                        log.warn("DAG-SUGGESTION 失败，跳过: submissionId={}, error={}",
                                state.getSubmissionId(), e.getMessage());
                        state.setSuggestions(Collections.emptyList());
                        state.setCompleted(true);
                        return "done_skipped";
                    }
                }
        ));

        // ── 边定义（无条件串行） ──
        wf.addEdge("GRADE", "ERROR_ANALYSIS");
        wf.addEdge("ERROR_ANALYSIS", "SUGGESTION");

        return wf;
    }

    /**
     * 执行批改工作流
     *
     * @param state 包含所有输入参数的初始状态
     * @return 执行后的状态（包含各节点产出）
     */
    public GradingState execute(GradingState state) {
        log.info("启动批改工作流: submissionId={}", state.getSubmissionId());
        GradingState result = engine.execute(buildDefinition(), state);

        log.info("批改工作流完成: instanceId={}, 执行轨迹={}",
                result.getInstanceId(), engine.getTrace(result.getInstanceId()));

        return result;
    }

    /**
     * 将 DAG 各节点产出合并为完整的 EvaluationResultDTO
     */
    public EvaluationResultDTO mergeToEvaluationResult(GradingState state) {
        EvaluationResultDTO result = new EvaluationResultDTO();

        EvaluationResultDTO grade = state.getGradeResult();
        if (grade != null) {
            result.setTotalScore(grade.getTotalScore());
            result.setContentScore(grade.getContentScore());
            result.setFormatScore(grade.getFormatScore());
            result.setMaxScore(grade.getMaxScore());
            result.setGrade(grade.getGrade());
            result.setOverallComment(grade.getOverallComment());
            result.setStrengths(grade.getStrengths());
            result.setWeaknesses(grade.getWeaknesses());
            result.setKnowledgePoints(grade.getKnowledgePoints());
            result.setScoringDetails(grade.getScoringDetails());
        }

        result.setErrors(state.getErrors() != null ? state.getErrors() : Collections.emptyList());
        result.setSuggestions(state.getSuggestions() != null ? state.getSuggestions() : Collections.emptyList());

        return result;
    }

    private void validateGradeResult(EvaluationResultDTO result) {
        List<String> violations = new ArrayList<>();
        if (result.getTotalScore() == null) violations.add("totalScore: is required");
        if (result.getContentScore() == null) violations.add("contentScore: is required");
        if (result.getFormatScore() == null) violations.add("formatScore: is required");
        if (result.getMaxScore() == null) violations.add("maxScore: is required");
        if (result.getOverallComment() == null || result.getOverallComment().isBlank()) {
            violations.add("overallComment: is required");
        }
        if (result.getTotalScore() != null && result.getMaxScore() != null
                && result.getTotalScore() > result.getMaxScore()) {
            violations.add("totalScore: must not exceed maxScore");
        }
        if (!violations.isEmpty()) throw new StructuredOutputValidationException(violations);
    }

    private void requireList(String field, List<?> value) {
        if (value == null) {
            throw new StructuredOutputValidationException(List.of(field + ": is required"));
        }
    }

    // ==================== Prompt 构建 ====================

    private String buildGradePrompt(GradingState state) {
        String template = promptLoader.load("grading-grade.txt");
        return template
                .replace("{{taskDescription}}", state.getTaskDescription())
                .replace("{{studentName}}", state.getStudentName())
                .replace("{{className}}", state.getClassName())
                .replace("{{assignmentName}}", state.getAssignmentName())
                .replace("{{studentNote}}", state.getStudentNote())
                .replace("{{fileContent}}", state.getFileContent())
                .replace("{{kpContext}}", state.getKpContext());
    }

    private String buildErrorAnalysisPrompt(GradingState state) {
        String template = promptLoader.load("grading-errors.txt");

        EvaluationResultDTO grade = state.getGradeResult();
        String totalScore = grade != null && grade.getTotalScore() != null
                ? String.valueOf(grade.getTotalScore()) : "0";
        String overallComment = grade != null && grade.getOverallComment() != null
                ? grade.getOverallComment() : "";
        String strengths = formatList(grade != null ? grade.getStrengths() : null, "无");
        String weaknesses = formatList(grade != null ? grade.getWeaknesses() : null, "无");

        return template
                .replace("{{totalScore}}", totalScore)
                .replace("{{overallComment}}", overallComment)
                .replace("{{strengths}}", strengths)
                .replace("{{weaknesses}}", weaknesses)
                .replace("{{kpContext}}", state.getKpContext());
    }

    private String buildSuggestionPrompt(GradingState state) {
        String template = promptLoader.load("grading-suggestions.txt");

        EvaluationResultDTO grade = state.getGradeResult();
        String totalScore = grade != null && grade.getTotalScore() != null
                ? String.valueOf(grade.getTotalScore()) : "0";
        String overallComment = grade != null && grade.getOverallComment() != null
                ? grade.getOverallComment() : "";
        String weaknesses = formatList(grade != null ? grade.getWeaknesses() : null, "无");

        List<EvaluationResultDTO.ErrorItem> errors = state.getErrors();
        String errorsSummary;
        if (errors != null && !errors.isEmpty()) {
            errorsSummary = errors.stream()
                    .map(e -> String.format("- [%s][%s] %s (知识点: %s)",
                            e.getSeverity() != null ? e.getSeverity() : "minor",
                            e.getType() != null ? e.getType() : "",
                            e.getIssue() != null ? e.getIssue() : "",
                            e.getKnowledgePoint() != null ? e.getKnowledgePoint() : "其他"))
                    .collect(Collectors.joining("\n"));
        } else {
            errorsSummary = "无具体错误记录";
        }

        return template
                .replace("{{totalScore}}", totalScore)
                .replace("{{overallComment}}", overallComment)
                .replace("{{weaknesses}}", weaknesses)
                .replace("{{errorsSummary}}", errorsSummary)
                .replace("{{kpContext}}", state.getKpContext());
    }

    private String formatList(List<String> items, String defaultValue) {
        if (items == null || items.isEmpty()) return defaultValue;
        return items.stream()
                .map(s -> "- " + s)
                .collect(Collectors.joining("\n"));
    }

    // ==================== 工作流状态 ====================

    @lombok.Data
    @lombok.EqualsAndHashCode(callSuper = true)
    public static class GradingState extends WorkflowState {

        // ── 输入参数 ──
        private Long submissionId;
        private String studentName;
        private String className;
        private String assignmentName;
        private String studentNote;
        private String taskDescription;
        private String fileContent;
        private String kpContext;

        // ── 节点产出 ──
        private EvaluationResultDTO gradeResult;
        private List<EvaluationResultDTO.ErrorItem> errors;
        private List<EvaluationResultDTO.SuggestionItem> suggestions;

        /**
         * 从 Submission 创建 GradingState
         */
        public static GradingState create(Submission sub, String taskDescription,
                                          String studentRequirement, String fileContent,
                                          String kpContext) {
            String studentNote = (studentRequirement != null && !studentRequirement.isEmpty())
                    ? "\n\n【学生提交说明】\n" + studentRequirement + "\n"
                    : "";

            GradingState s = new GradingState();
            s.setInstanceId("grading-" + sub.getId() + "-" + System.currentTimeMillis());
            s.setStartTime(LocalDateTime.now());
            s.submissionId = sub.getId();
            s.studentName = sub.getStudentName() != null ? sub.getStudentName() : "";
            s.className = sub.getClassName() != null ? sub.getClassName() : "";
            s.assignmentName = sub.getAssignmentName() != null ? sub.getAssignmentName() : "";
            s.studentNote = studentNote;
            s.taskDescription = taskDescription != null && !taskDescription.isEmpty()
                    ? taskDescription : "无特殊要求";
            s.fileContent = fileContent;
            s.kpContext = kpContext != null ? kpContext : "";
            return s;
        }
    }
}
