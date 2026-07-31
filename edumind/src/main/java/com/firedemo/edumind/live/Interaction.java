package com.firedemo.edumind.live;

import com.baomidou.mybatisplus.annotation.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;

@Data @Builder @NoArgsConstructor @AllArgsConstructor(access = lombok.AccessLevel.PRIVATE)
@TableName("interaction")
public class Interaction {
    @TableId(type = IdType.AUTO) private Long id;
    private Long questionId;
    private Long sessionId;
    private String type;
    private String title;
    private String description;
    private String options;
    private String correctKey;
    private Integer timeLimit;
    private String status;
    private Integer sortOrder;
    private Boolean aiGenerated;
    private String knowledgePoint;
    private String difficulty;
    private String explanation;
    private Long classId;
    private String sourceDocId;  // 来源文档 ID
    @TableField(fill = FieldFill.INSERT) private LocalDateTime createdAt;
    private LocalDateTime activatedAt;
    private LocalDateTime deadlineAt;
    private LocalDateTime closedAt;
}
