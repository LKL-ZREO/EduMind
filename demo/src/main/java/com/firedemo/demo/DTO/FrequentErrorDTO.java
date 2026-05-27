package com.firedemo.demo.DTO;

import lombok.Data;

/**
 * 高频错题DTO
 */
@Data
public class FrequentErrorDTO {

    private String question;
    private String difficulty;
    private String difficultyLabel;
    private Integer errorRate;
    private Integer errorCount;
    /** 归属知识点 */
    private String knowledgePoint;
}
