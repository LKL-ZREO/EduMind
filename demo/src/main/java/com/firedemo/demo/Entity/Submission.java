package com.firedemo.demo.Entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 作业提交记录（学生端匿名提交）
 */
@Data
@TableName("submission")
public class Submission {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String studentName;

    private String className;

    private Long classId;

    private String assignmentName;

    private String fileName;

    private String filePath;

    private Long fileSize;

    private Integer totalScore;

    private Integer contentScore;

    private Integer formatScore;

    private String overallComment;

    private String strengths;

    private String weaknesses;

    private String suggestions;

    private String rawResponse;

    private LocalDateTime submittedAt;

    /** 关联的作业任务ID */
    private Long taskId;

    /** 当前提交次数（第几次） */
    private Integer submitCount;

    /** 剩余提交次数 */
    private Integer remainingAttempts;

    /** 是否逾期提交 */
    private Boolean isLate;

    /** 是否应用了逾期扣分 */
    private Boolean penaltyApplied;

    /** 扣分后的最终分数 */
    private Integer finalScore;

    /** 作业序号（用于成长曲线） */
    private Integer assignmentNo;
}
