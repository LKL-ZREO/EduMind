package com.firedemo.demo.Service;

import com.firedemo.demo.Entity.HomeworkEvaluation;

/**
 * 作业批改结果 Service（评价）
 */
public interface HomeworkResultService {

    /** 保存评价 */
    void saveEvaluation(HomeworkEvaluation evaluation);

    /** 查询班级薄弱知识点列表（从 submission_errors 统计） */
    java.util.List<String> listWeakKnowledgePoints(Long classId);
}
