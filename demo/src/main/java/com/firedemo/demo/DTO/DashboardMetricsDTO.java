package com.firedemo.demo.DTO;

import lombok.Data;

/**
 * 仪表盘核心指标DTO
 */
@Data
public class DashboardMetricsDTO {

    private Integer totalStudents;
    private Integer studentTrend;
    private Integer totalHomework;
    private Integer newHomework;
    private Double avgScore;
    private Double scoreTrend;
    private Integer warningStudents;
}
