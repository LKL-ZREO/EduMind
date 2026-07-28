package com.firedemo.demo.Entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 课堂实时互动 — 学生对推送到题目标记"不懂"的事件。
 * AI 即时生成解析反馈给学生，课后教师查看按知识点汇总。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor(access = lombok.AccessLevel.PRIVATE)
@TableName("live_confusion_event")
public class LiveConfusionEvent {
    @TableId(type = IdType.AUTO)
    private Long id;

    private Long sessionId;
    private Long interactionId;
    private String studentId;
    private String studentName;
    private String knowledgePoint;
    private String questionText;
    private String aiExplanation;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}
