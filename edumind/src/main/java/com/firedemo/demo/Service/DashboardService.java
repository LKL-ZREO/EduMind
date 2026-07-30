package com.firedemo.demo.Service;

import com.firedemo.demo.DTO.*;
import com.firedemo.demo.Entity.TeacherKnowledge;

import java.util.List;

/**
 * 仪表盘数据服务接口
 */
public interface DashboardService {

    /** 获取核心指标 */
    DashboardMetricsDTO getMetrics(Long classId);

    /** 获取成绩分布 */
    List<ScoreDistributionDTO> getScoreDistribution(Long classId);

    /** 获取知识点掌握度热力图（从 teacher_knowledge + submission_errors 聚合） */
    List<KnowledgeMasteryDTO> getKnowledgeMastery(Long classId);

    /** 获取高频错题，支持按知识点筛选 */
    List<FrequentErrorDTO> getFrequentErrors(Long classId, String knowledgePoint);

    /** 获取学生概览列表 */
    List<StudentOverviewDTO> getStudentOverview(Long classId, String sortBy, String keyword);

    /** 获取单个学生的综合学习诊断 */
    StudentInsightDTO getStudentInsight(Long classId, String studentId, String studentName);

    /** 获取班级列表 */
    List<ClassInfoDTO> getClassList(Long teacherId);

    // ========== 老师知识管理 ==========

    /** 查询班级的自定义知识点 */
    List<TeacherKnowledge> getTeacherKnowledge(Long classId);

    /** 批量同步班级自定义知识点，并迁移受影响的历史错误分类 */
    void saveTeacherKnowledge(Long classId, Long userId, List<TeacherKnowledgeDTO> items);

    /** 添加单个知识点 */
    void addTeacherKnowledge(Long classId, Long userId, String name, String color);

    /** 删除单个知识点，将历史错误移入“其他”，返回班级ID */
    Long deleteTeacherKnowledge(Long id);
}
