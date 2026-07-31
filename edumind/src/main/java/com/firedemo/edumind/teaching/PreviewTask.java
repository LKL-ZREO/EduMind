package com.firedemo.edumind.teaching;

import com.baomidou.mybatisplus.annotation.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 预习任务 — AI 生成的课前预习材料。
 */
@Data @Builder @NoArgsConstructor @AllArgsConstructor(access = lombok.AccessLevel.PRIVATE)
@TableName("preview_task")
public class PreviewTask {
    @TableId(type = IdType.AUTO)
    private Long id;

    private Long classId;
    private Long teacherId;
    private String title;             // 预习标题
    private String knowledgePoint;    // 对应知识点

    @TableField("guide_text")
    private String guideText;         // AI 导读材料（Markdown）

    private String questionsJson;     // JSONB: [{"question":"...","options":[...],"correctKey":"A"}, ...]

    @TableField("discussion_question")
    private String discussionQuestion; // 课堂讨论题

    private String sourceDocId;       // 来源文档 ID

    private String status;            // ACTIVE / CLOSED

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
