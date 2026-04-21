package com.firedemo.demo.Service;

import com.firedemo.demo.DTO.*;

import java.util.List;

/**
 * 仪表盘数据服务接口
 */
public interface DashboardService {

    /**
     * 获取核心指标
     */
    DashboardMetricsDTO getMetrics(Long classId);

    /**
     * 获取成绩分布
     */
    List<ScoreDistributionDTO> getScoreDistribution(Long classId);

    /**
     * 获取知识点掌握度
     */
    List<KnowledgeMasteryDTO> getKnowledgeMastery(Long classId);

    /**
     * 获取高频错题
     */
    List<FrequentErrorDTO> getFrequentErrors(Long classId);

    /**
     * 获取学生概览列表
     */
    List<StudentOverviewDTO> getStudentOverview(Long classId, String sortBy, String keyword);

    /**
     * 获取班级列表
     */
    List<ClassInfoDTO> getClassList(Long teacherId);
}
