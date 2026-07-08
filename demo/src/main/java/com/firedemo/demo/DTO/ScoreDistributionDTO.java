package com.firedemo.demo.DTO;

import lombok.Data;

/**
 * 成绩分布DTO
 */
@Data
public class ScoreDistributionDTO {

    private String range;
    private Integer count;
    private Double percentage;
    private String color;
}
