package com.firedemo.demo.Service.ServiceImpl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.firedemo.demo.Entity.Submission;
import com.firedemo.demo.Service.SubmissionService;
import com.firedemo.demo.mapper.SubmissionMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class SubmissionServiceImpl implements SubmissionService {

    private final SubmissionMapper submissionMapper;

    @Override
    public Submission getById(Long id) {
        return submissionMapper.selectById(id);
    }

    @Override
    public void create(Submission submission) {
        submissionMapper.insert(submission);
    }

    @Override
    public List<Submission> listByClassId(Long classId) {
        return submissionMapper.selectByClassId(classId);
    }

    @Override
    public List<Submission> listByStudentAndClass(String studentName, Long classId) {
        return submissionMapper.selectByStudentAndClass(studentName, classId);
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
    public Integer countByClassId(Long classId) {
        return submissionMapper.countByClassId(classId);
    }

    @Override
    public Integer countByStudentIdAndTaskId(String studentId, Long taskId) {
        return submissionMapper.countByStudentIdAndTaskId(studentId, taskId);
    }

    @Override
    public List<Integer> listScoresByClassId(Long classId) {
        return submissionMapper.selectScoresByClassId(classId);
    }

    @Override
    public List<Map<String, Object>> listKnowledgeStatsByClassId(Long classId) {
        return submissionMapper.selectKnowledgeStatsByClassId(classId);
    }

    @Override
    public List<String> listWeakKnowledgePoints(Long classId) {
        return submissionMapper.selectWeakKnowledgePoints(classId);
    }

    @Override
    public List<Map<String, Object>> listStudentOverviewByClassId(Long classId) {
        return submissionMapper.selectStudentOverviewByClassId(classId);
    }

    @Override
    public Integer countDistinctStudentsByClassId(Long classId) {
        return submissionMapper.countDistinctStudentsByClassId(classId);
    }

    @Override
    public List<Map<String, Object>> listTaskStatsByClassId(Long classId) {
        return submissionMapper.selectTaskStatsByClassId(classId);
    }

    @Override
    public List<String> listRawResponsesByClassId(Long classId) {
        return submissionMapper.selectRawResponsesByClassId(classId);
    }

    @Override
    public Integer countNewByClassId(Long classId, LocalDateTime since) {
        return submissionMapper.countNewByClassId(classId, since);
    }

    @Override
    public List<Submission> listByTaskId(Long taskId) {
        return submissionMapper.selectList(
                new LambdaQueryWrapper<Submission>()
                        .eq(Submission::getTaskId, taskId)
                        .orderByDesc(Submission::getSubmittedAt));
    }
}
