package com.firedemo.demo.Entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 作业评价实体
 */
@Data
@TableName("homework_evaluation")
public class HomeworkEvaluation {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;

    private Long classId;

    private String sessionId;

    private String filePath;

    private String requirement;

    // 评分字段
    private Integer totalScore;

    private Integer contentScore;

    private Integer formatScore;

    // 评语
    private String overallComment;

    private String strengths;

    private String weaknesses;

    private String suggestions;

    // 原始响应
    private String rawResponse;

    private LocalDateTime createdAt;
}
