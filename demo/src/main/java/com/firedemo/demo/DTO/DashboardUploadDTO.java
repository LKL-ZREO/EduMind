package com.firedemo.demo.DTO;

import lombok.Data;
import java.util.List;

/**
 * 仪表盘数据上传DTO
 */
@Data
public class DashboardUploadDTO {
    
    /**
     * 班级ID
     */
    private Long classId;
    
    /**
     * 班级名称
     */
    private String className;
    
    /**
     * 核心指标
     */
    private DashboardMetricsDTO metrics;
    
    /**
     * 成绩分布
     */
    private List<ScoreDistributionDTO> scoreDistribution;
    
    /**
     * 知识点掌握度
     */
    private List<KnowledgeMasteryDTO> knowledgeMastery;
    
    /**
     * 高频错题
     */
    private List<FrequentErrorDTO> frequentErrors;
    
    /**
     * 学生列表
     */
    private List<StudentOverviewDTO> students;
    
    /**
     * 导出时间
     */
    private String exportTime;
}
