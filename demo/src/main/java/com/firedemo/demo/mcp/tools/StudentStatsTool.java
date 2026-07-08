package com.firedemo.demo.mcp.tools;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.firedemo.demo.Entity.Submission;
import com.firedemo.demo.mcp.ToolDefinition;
import com.firedemo.demo.mapper.SubmissionMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * MCP 工具：学生成绩统计查询
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class StudentStatsTool implements ToolDefinition {

    private final SubmissionMapper submissionMapper;

    @Override
    public String name() {
        return "queryStudentStats";
    }

    @Override
    public String description() {
        return "查询学生的作业成绩统计，包括平均分、提交次数、最近分数、薄弱知识点";
    }

    @Override
    public Map<String, Object> inputSchema() {
        return Map.of(
                "type", "object",
                "properties", Map.of(
                        "studentName", Map.of("type", "string", "description", "学生姓名")
                ),
                "required", List.of("studentName")
        );
    }

    @Override
    public String execute(Map<String, Object> arguments) {
        String studentName = (String) arguments.get("studentName");
        log.info("MCP Tool queryStudentStats: student={}", studentName);

        try {
            List<Submission> submissions = submissionMapper.selectList(
                    new LambdaQueryWrapper<Submission>()
                            .eq(Submission::getStudentName, studentName)
                            .orderByDesc(Submission::getSubmittedAt));

            if (submissions == null || submissions.isEmpty()) {
                return "未找到学生「" + studentName + "」的提交记录。请确认姓名是否正确。";
            }

            long totalSubmissions = submissions.size();

            List<Submission> scored = submissions.stream()
                    .filter(s -> s.getTotalScore() != null)
                    .toList();
            double avgScore = scored.isEmpty() ? 0
                    : scored.stream().mapToInt(Submission::getTotalScore).average().orElse(0);

            Submission latest = submissions.get(0);
            String latestInfo = (latest.getAssignmentName() != null ? latest.getAssignmentName() : "未命名作业")
                    + " | " + (latest.getClassName() != null ? latest.getClassName() : "")
                    + " | " + (latest.getTotalScore() != null ? latest.getTotalScore() + "分" : "批改中");

            String trend = scored.stream().limit(3)
                    .map(s -> (s.getAssignmentName() != null ? s.getAssignmentName() : "?") + ":" + s.getTotalScore() + "分")
                    .collect(Collectors.joining(" → "));

            return String.format("""
                    学生：%s
                    总提交次数：%d 次
                    平均分：%.1f 分
                    最近一次：%s
                    近期趋势：%s
                    """, studentName, totalSubmissions, avgScore, latestInfo,
                    trend.isEmpty() ? "暂无数据" : trend);

        } catch (Exception e) {
            log.error("查询学生成绩失败", e);
            return "查询失败: " + e.getMessage();
        }
    }
}
