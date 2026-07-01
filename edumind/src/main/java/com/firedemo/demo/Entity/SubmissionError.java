package com.firedemo.demo.Entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 作业错误分类明细（知识点归属）
 */
@Data
@TableName("submission_errors")
public class SubmissionError {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long submissionId;

    private Long classId;

    private String errorText;

    private String errorType;

    private String severity;

    private String knowledgePoint;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
