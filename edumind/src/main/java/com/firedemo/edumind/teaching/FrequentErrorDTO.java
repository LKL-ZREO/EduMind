package com.firedemo.edumind.teaching;

import lombok.Data;

import java.time.LocalDateTime;

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
    /** 出现该错误的去重学生数 */
    private Integer affectedStudentCount;
    /** 受影响学生占有效学生的比例（0-100） */
    private Double affectedStudentRate;
    /** 出现该错误的作业数量 */
    private Integer assignmentCount;
    /** 最近一次出现时间 */
    private LocalDateTime latestSeenAt;
}
