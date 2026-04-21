package com.firedemo.demo.DTO;

import lombok.Data;

/**
 * 学生概览DTO
 */
@Data
public class StudentOverviewDTO {

    private Long id;
    private String name;
    private Integer avgScore;
    private Integer homeworkCount;
    private Integer errorCount;
    private Integer trend;
    private Boolean needAttention;
}
