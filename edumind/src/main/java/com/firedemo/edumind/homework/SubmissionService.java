package com.firedemo.edumind.homework;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class SubmissionService {

    private final SubmissionMapper submissionMapper;
    public void save(Submission submission) {
        submissionMapper.insert(submission);
    }
    public Submission getById(Long id) {
        return submissionMapper.selectById(id);
    }
    public List<Submission> listByStudentAndClassOrderByNo(String studentName, Long classId) {
        return submissionMapper.selectByStudentAndClassOrderByNo(studentName, classId);
    }
    public List<Submission> listByStudentIdAndClassOrderByNo(String studentId, Long classId) {
        return submissionMapper.selectByStudentIdAndClassOrderByNo(studentId, classId);
    }
    public List<Submission> listByTaskId(Long taskId) {
        return submissionMapper.selectList(
                new LambdaQueryWrapper<Submission>()
                        .eq(Submission::getTaskId, taskId)
                        .orderByDesc(Submission::getSubmittedAt));
    }
    public Integer countByStudentIdAndTaskId(String studentId, Long taskId) {
        return submissionMapper.countByStudentIdAndTaskId(studentId, taskId);
    }
    public List<Map<String, Object>> listTaskStatsByClassId(Long classId) {
        return submissionMapper.selectTaskStatsByClassId(classId);
    }
}
