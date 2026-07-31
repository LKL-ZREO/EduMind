package com.firedemo.edumind.assistant.tool;

import com.firedemo.edumind.classroom.ClassInfo;
import com.firedemo.edumind.assistant.context.AgentExecutionContext;
import com.firedemo.edumind.teaching.LegacyHomeworkEvaluationStatsMapper;
import com.firedemo.edumind.homework.SubmissionErrorMapper;
import com.firedemo.edumind.homework.SubmissionMapper;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ClassStatusToolTest {

    @Test
    void combinesLegacyAndCurrentSubmissionStatistics() {
        ToolAccessPolicy accessPolicy = mock(ToolAccessPolicy.class);
        SubmissionMapper submissionMapper = mock(SubmissionMapper.class);
        LegacyHomeworkEvaluationStatsMapper legacyStats =
                mock(LegacyHomeworkEvaluationStatsMapper.class);
        SubmissionErrorMapper submissionErrorMapper = mock(SubmissionErrorMapper.class);
        AgentExecutionContext context = mock(AgentExecutionContext.class);

        ClassInfo classInfo = new ClassInfo();
        classInfo.setId(10L);
        when(accessPolicy.findOwnedClass(context, "高数一班")).thenReturn(classInfo);
        when(submissionMapper.countDistinctStudentsByClassId(10L)).thenReturn(20);
        when(submissionMapper.countByClassId(10L)).thenReturn(3);
        when(submissionMapper.selectScoresByClassId(10L)).thenReturn(List.of(80, 90));
        when(legacyStats.countByClassId(10L)).thenReturn(2);
        when(legacyStats.selectScoresByClassId(10L)).thenReturn(List.of(60));
        when(submissionErrorMapper.selectWeakKnowledgePoints(10L)).thenReturn(List.of());

        ClassStatusTool tool = new ClassStatusTool(
                accessPolicy, submissionMapper, legacyStats, submissionErrorMapper);

        String result = tool.execute(Map.of("className", "高数一班"), context);

        assertThat(result)
                .contains("参与学生数：20 人")
                .contains("作业总数：5 份")
                .contains("平均分：76.7 分");
    }
}
