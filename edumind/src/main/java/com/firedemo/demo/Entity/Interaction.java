package com.firedemo.demo.Entity;

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
    private Long classId;
    private String sourceDocId;  // 来源文档 ID
    @TableField(fill = FieldFill.INSERT) private LocalDateTime createdAt;
    private LocalDateTime closedAt;
}
