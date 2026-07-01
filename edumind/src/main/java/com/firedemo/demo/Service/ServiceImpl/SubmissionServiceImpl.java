package com.firedemo.demo.Service.ServiceImpl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.firedemo.demo.Entity.Submission;
import com.firedemo.demo.Service.SubmissionService;
import com.firedemo.demo.mapper.SubmissionMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class SubmissionServiceImpl implements SubmissionService {

    private final SubmissionMapper submissionMapper;

    @Override
    public void save(Submission submission) {
        submissionMapper.insert(submission);
    }

    @Override
    public Submission getById(Long id) {
        return submissionMapper.selectById(id);
    }

    @Override
    public List<Submission> listByStudentAndClassOrderByNo(String studentName, Long classId) {
        return submissionMapper.selectByStudentAndClassOrderByNo(studentName, classId);
    }

    @Override
    public List<Submission> listByStudentIdAndClassOrderByNo(String studentId, Long classId) {
        return submissionMapper.selectByStudentIdAndClassOrderByNo(studentId, classId);
    }

    @Override
    public List<Submission> listByTaskId(Long taskId) {
        return submissionMapper.selectList(
                new LambdaQueryWrapper<Submission>()
                        .eq(Submission::getTaskId, taskId)
                        .orderByDesc(Submission::getSubmittedAt));
    }

    @Override
    public Integer countByStudentIdAndTaskId(String studentId, Long taskId) {
        return submissionMapper.countByStudentIdAndTaskId(studentId, taskId);
    }

    @Override
    public List<Map<String, Object>> listTaskStatsByClassId(Long classId) {
        return submissionMapper.selectTaskStatsByClassId(classId);
    }
}
