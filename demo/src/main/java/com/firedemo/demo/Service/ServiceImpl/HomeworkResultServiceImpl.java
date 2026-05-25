package com.firedemo.demo.Service.ServiceImpl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.firedemo.demo.Entity.HomeworkEvaluation;
import com.firedemo.demo.Entity.HomeworkKnowledge;
import com.firedemo.demo.Service.HomeworkResultService;
import com.firedemo.demo.mapper.HomeworkEvaluationMapper;
import com.firedemo.demo.mapper.HomeworkKnowledgeMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class HomeworkResultServiceImpl implements HomeworkResultService {

    private final HomeworkEvaluationMapper evaluationMapper;
    private final HomeworkKnowledgeMapper knowledgeMapper;

    @Override
    public void saveEvaluation(HomeworkEvaluation evaluation) {
        evaluationMapper.insert(evaluation);
    }

    @Override
    public void saveKnowledge(HomeworkKnowledge knowledge) {
        knowledgeMapper.insert(knowledge);
    }

    @Override
    public List<HomeworkKnowledge> listKnowledgeByEvaluationId(Long evaluationId) {
        return knowledgeMapper.selectList(
                new LambdaQueryWrapper<HomeworkKnowledge>()
                        .eq(HomeworkKnowledge::getEvaluationId, evaluationId));
    }

    @Override
    public List<Map<String, Object>> listKnowledgeStatsByClassId(Long classId) {
        return knowledgeMapper.selectKnowledgeStatsByClassId(classId);
    }

    @Override
    public List<String> listWeakKnowledgePoints(Long classId) {
        return knowledgeMapper.selectWeakKnowledgePoints(classId);
    }
}
