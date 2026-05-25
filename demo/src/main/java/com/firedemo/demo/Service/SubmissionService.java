package com.firedemo.demo.Service;

import com.firedemo.demo.Entity.Submission;

import java.util.List;
import java.util.Map;

/**
 * 作业提交 Service
 */
public interface SubmissionService {

    Submission getById(Long id);

    void create(Submission submission);

    List<Submission> listByClassId(Long classId);

    List<Submission> listByStudentAndClass(String studentName, Long classId);

    List<Submission> listByStudentAndClassOrderByNo(String studentName, Long classId);

    List<Submission> listByStudentIdAndClassOrderByNo(String studentId, Long classId);

    Integer countByClassId(Long classId);

    Integer countByStudentIdAndTaskId(String studentId, Long taskId);

    List<Integer> listScoresByClassId(Long classId);

    List<Map<String, Object>> listKnowledgeStatsByClassId(Long classId);

    List<String> listWeakKnowledgePoints(Long classId);

    List<Map<String, Object>> listStudentOverviewByClassId(Long classId);

    Integer countDistinctStudentsByClassId(Long classId);

    List<Map<String, Object>> listTaskStatsByClassId(Long classId);

    List<String> listRawResponsesByClassId(Long classId);

    Integer countNewByClassId(Long classId, java.time.LocalDateTime since);

    List<Submission> listByTaskId(Long taskId);
}
