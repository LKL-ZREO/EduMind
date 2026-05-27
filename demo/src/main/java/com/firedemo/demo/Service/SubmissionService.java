package com.firedemo.demo.Service;

import com.firedemo.demo.Entity.Submission;

import java.util.List;
import java.util.Map;

/**
 * 提交记录 Service 接口（学生端匿名提交）
 */
public interface SubmissionService {

    void save(Submission submission);

    Submission getById(Long id);

    List<Submission> listByStudentAndClassOrderByNo(String studentName, Long classId);

    List<Submission> listByStudentIdAndClassOrderByNo(String studentId, Long classId);

    List<Submission> listByTaskId(Long taskId);

    Integer countByStudentIdAndTaskId(String studentId, Long taskId);

    List<Map<String, Object>> listTaskStatsByClassId(Long classId);
}
