package com.firedemo.demo.Service.ServiceImpl;

import com.firedemo.demo.Entity.HomeworkEvaluation;
import com.firedemo.demo.Service.HomeworkResultService;
import com.firedemo.demo.mapper.HomeworkEvaluationMapper;
import com.firedemo.demo.mapper.SubmissionErrorMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class HomeworkResultServiceImpl implements HomeworkResultService {

    private final HomeworkEvaluationMapper evaluationMapper;
    private final SubmissionErrorMapper submissionErrorMapper;

    @Override
    public void saveEvaluation(HomeworkEvaluation evaluation) {
        evaluationMapper.insert(evaluation);
    }

    @Override
    public List<String> listWeakKnowledgePoints(Long classId) {
        // 从 submission_errors 统计：错误数最多的知识点即为薄弱点
        List<Map<String, Object>> stats = submissionErrorMapper.selectWeakKnowledgePoints(classId);
        return stats.stream()
                .map(row -> (String) row.get("knowledge_point"))
                .filter(name -> name != null && !"其他".equals(name))
                .collect(Collectors.toList());
    }
}
