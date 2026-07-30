package com.firedemo.demo.Service;

import com.firedemo.demo.DTO.StudentInsightDTO;
import com.firedemo.demo.DTO.TeacherKnowledgeDTO;
import com.firedemo.demo.Entity.Submission;
import com.firedemo.demo.Entity.TeacherKnowledge;
import com.firedemo.demo.Service.ServiceImpl.DashboardServiceImpl;
import com.firedemo.demo.common.exception.BusinessException;
import com.firedemo.demo.mapper.ClassInfoMapper;
import com.firedemo.demo.mapper.LegacyHomeworkEvaluationStatsMapper;
import com.firedemo.demo.mapper.SubmissionErrorMapper;
import com.firedemo.demo.mapper.SubmissionMapper;
import com.firedemo.demo.mapper.TeacherKnowledgeMapper;
import com.firedemo.demo.mapper.UserMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.cache.CacheManager;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.startsWith;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DashboardServiceImplTest {

    private SubmissionMapper submissionMapper;
    private TeacherKnowledgeMapper teacherKnowledgeMapper;
    private SubmissionErrorMapper submissionErrorMapper;
    private DashboardServiceImpl service;

    @BeforeEach
    void setUp() {
        submissionMapper = mock(SubmissionMapper.class);
        teacherKnowledgeMapper = mock(TeacherKnowledgeMapper.class);
        submissionErrorMapper = mock(SubmissionErrorMapper.class);
        service = new DashboardServiceImpl(
                mock(LegacyHomeworkEvaluationStatsMapper.class),
                mock(UserMapper.class),
                mock(ClassInfoMapper.class),
                submissionMapper,
                teacherKnowledgeMapper,
                submissionErrorMapper,
                mock(CacheManager.class));
    }

    @Test
    void renamingKnowledgePointMigratesHistoricalErrorsWithoutRecreatingIdentity() {
        TeacherKnowledge pointer = knowledge(10L, 1L, "指针");
        TeacherKnowledge other = knowledge(11L, 1L, "其他");
        when(teacherKnowledgeMapper.exists(1L, "其他")).thenReturn(true);
        when(teacherKnowledgeMapper.selectByClassId(1L)).thenReturn(List.of(pointer, other));

        TeacherKnowledgeDTO request = new TeacherKnowledgeDTO();
        request.setId(10L);
        request.setName("地址与指针");
        request.setColor("#655bd7");
        request.setSortOrder(0);

        service.saveTeacherKnowledge(1L, 99L, List.of(request));

        verify(submissionErrorMapper).updateKnowledgePoint(eq(1L), eq("指针"), startsWith("__kp_tmp_10_"));
        ArgumentCaptor<String> temporaryName = ArgumentCaptor.forClass(String.class);
        verify(submissionErrorMapper).updateKnowledgePoint(eq(1L), temporaryName.capture(), eq("地址与指针"));
        assertThat(temporaryName.getValue()).startsWith("__kp_tmp_10_");
        assertThat(pointer.getId()).isEqualTo(10L);
        assertThat(pointer.getName()).isEqualTo("地址与指针");
        verify(teacherKnowledgeMapper, never()).deleteById(10L);
    }

    @Test
    void deletingKnowledgePointMovesErrorsToOtherAndProtectsFallback() {
        TeacherKnowledge pointer = knowledge(10L, 1L, "指针");
        when(teacherKnowledgeMapper.selectById(10L)).thenReturn(pointer);

        assertThat(service.deleteTeacherKnowledge(10L)).isEqualTo(1L);
        verify(submissionErrorMapper).updateKnowledgePoint(1L, "指针", "其他");
        verify(teacherKnowledgeMapper).deleteById(10L);

        TeacherKnowledge other = knowledge(11L, 1L, "其他");
        when(teacherKnowledgeMapper.selectById(11L)).thenReturn(other);
        assertThatThrownBy(() -> service.deleteTeacherKnowledge(11L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("不可删除");
    }

    @Test
    void buildsStudentInsightFromScoresAndErrorEvidence() {
        LocalDateTime now = LocalDateTime.now();
        when(submissionMapper.selectByStudentIdAndClassOrderByNo("S01", 1L)).thenReturn(List.of(
                submission(1L, "作业一", 80, now.minusDays(3), false),
                submission(2L, "作业二", 70, now.minusDays(2), false),
                submission(3L, "作业三", 55, now.minusDays(1), true)));
        when(submissionErrorMapper.selectStudentKnowledgeStats(1L, "S01", "张三", 8))
                .thenReturn(List.of(Map.of(
                        "name", "数组",
                        "error_count", 6L,
                        "critical_count", 2L,
                        "latest_seen_at", now)));
        when(submissionErrorMapper.selectRecentStudentErrors(1L, "S01", "张三", 12))
                .thenReturn(List.of(Map.of(
                        "id", 100L,
                        "submission_id", 3L,
                        "assignment_name", "作业三",
                        "knowledge_point", "数组",
                        "error_text", "数组下标越界",
                        "severity", "critical",
                        "created_at", now)));

        StudentInsightDTO insight = service.getStudentInsight(1L, "S01", "张三");

        assertThat(insight.getSummary().getAvgScore()).isEqualTo(68);
        assertThat(insight.getSummary().getLatestScore()).isEqualTo(55);
        assertThat(insight.getSummary().getLatestChange()).isEqualTo(-15);
        assertThat(insight.getSummary().getLateCount()).isEqualTo(1);
        assertThat(insight.getRisk().getLevel()).isEqualTo("HIGH");
        assertThat(insight.getRisk().getReasons())
                .contains("最近两次成绩连续下降", "数组累计出现6条错误");
        assertThat(insight.getWeakKnowledgePoints()).singleElement()
                .extracting(StudentInsightDTO.WeakKnowledgePoint::getName)
                .isEqualTo("数组");
        assertThat(insight.getRecentErrors()).singleElement()
                .extracting(StudentInsightDTO.RecentError::getErrorText)
                .isEqualTo("数组下标越界");
    }

    private TeacherKnowledge knowledge(Long id, Long classId, String name) {
        TeacherKnowledge knowledge = new TeacherKnowledge();
        knowledge.setId(id);
        knowledge.setClassId(classId);
        knowledge.setName(name);
        knowledge.setColor("#1890ff");
        knowledge.setSortOrder(0);
        knowledge.setCreatedBy(99L);
        return knowledge;
    }

    private Submission submission(
            Long id, String assignmentName, int score, LocalDateTime submittedAt, boolean late) {
        return Submission.builder()
                .id(id)
                .studentId("S01")
                .studentName("张三")
                .classId(1L)
                .assignmentName(assignmentName)
                .totalScore(score)
                .assignmentNo(id.intValue())
                .submittedAt(submittedAt)
                .isLate(late)
                .build();
    }
}
