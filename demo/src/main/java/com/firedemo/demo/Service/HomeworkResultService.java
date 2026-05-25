package com.firedemo.demo.Service;

import com.firedemo.demo.Entity.HomeworkEvaluation;
import com.firedemo.demo.Entity.HomeworkKnowledge;

import java.util.List;
import java.util.Map;

/**
 * 作业批改结果 Service（评价 + 知识点）
 */
public interface HomeworkResultService {

    // ========== 评价 ==========
    void saveEvaluation(HomeworkEvaluation evaluation);

    // ========== 知识点 ==========
    void saveKnowledge(HomeworkKnowledge knowledge);

    List<HomeworkKnowledge> listKnowledgeByEvaluationId(Long evaluationId);

    List<Map<String, Object>> listKnowledgeStatsByClassId(Long classId);

    List<String> listWeakKnowledgePoints(Long classId);
}
