package com.firedemo.demo.mcp.tools;

import com.firedemo.demo.Entity.ClassInfo;
import com.firedemo.demo.Service.ClassService;
import com.firedemo.demo.mapper.HomeworkEvaluationMapper;
import com.firedemo.demo.mapper.SubmissionErrorMapper;
import com.firedemo.demo.mapper.SubmissionMapper;
import com.firedemo.demo.mcp.ToolDefinition;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * MCP 工具：班级学习概况查询
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ClassStatusTool implements ToolDefinition {

    private final ClassService classService;
    private final SubmissionMapper submissionMapper;
    private final HomeworkEvaluationMapper evaluationMapper;
    private final SubmissionErrorMapper submissionErrorMapper;

    @Override
    public String name() {
        return "queryClassStatus";
    }

    @Override
    public String description() {
        return "查询班级的整体学习情况，包括学生数、作业数、平均分、薄弱知识点";
    }

    @Override
    public Map<String, Object> inputSchema() {
        return Map.of(
                "type", "object",
                "properties", Map.of(
                        "className", Map.of("type", "string", "description", "班级名称，如 C101 班")
                ),
                "required", List.of("className")
        );
    }

    @Override
    public String execute(Map<String, Object> arguments) {
        String className = (String) arguments.get("className");
        log.info("MCP Tool queryClassStatus: className={}", className);

        try {
            ClassInfo classInfo = classService.getClassByName(className);
            if (classInfo == null) {
                return "未找到班级「" + className + "」，请确认班级名称是否正确。";
            }
            Long classId = classInfo.getId();

            Integer studentCount = submissionMapper.countDistinctStudentsByClassId(classId);
            Integer totalHomework = evaluationMapper.countByClassId(classId);
            List<Integer> scores = evaluationMapper.selectScoresByClassId(classId);
            double avgScore = scores.isEmpty() ? 0
                    : scores.stream().mapToInt(Integer::intValue).average().orElse(0);

            List<Map<String, Object>> weakPoints = submissionErrorMapper.selectWeakKnowledgePoints(classId);
            String weakStr = weakPoints.isEmpty() ? "暂无数据" : weakPoints.stream()
                    .limit(5)
                    .map(row -> row.get("knowledge_point") + "(" + row.get("error_count") + "次)")
                    .collect(Collectors.joining("、"));

            return String.format("""
                    班级：%s
                    参与学生数：%d 人
                    作业总数：%d 份
                    平均分：%.1f 分
                    薄弱知识点（按错误数排序）：%s
                    """, className,
                    studentCount != null ? studentCount : 0,
                    totalHomework != null ? totalHomework : 0,
                    avgScore, weakStr);

        } catch (Exception e) {
            log.error("查询班级概况失败", e);
            return "查询失败: " + e.getMessage();
        }
    }
}
