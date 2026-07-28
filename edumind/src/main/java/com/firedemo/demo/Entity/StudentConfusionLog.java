package com.firedemo.demo.Entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 学生通过 QQ 标记"不懂"的知识点记录。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor(access = lombok.AccessLevel.PRIVATE)
@TableName("student_confusion_log")
public class StudentConfusionLog {
    @TableId(type = IdType.AUTO)
    private Long id;

    private String qqNumber;
    private String studentName;
    private Long classId;
    private String question;
    private String knowledgePoint;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}
