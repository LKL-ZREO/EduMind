package com.firedemo.edumind.live;

import com.baomidou.mybatisplus.annotation.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;

@Data @Builder @NoArgsConstructor @AllArgsConstructor(access = lombok.AccessLevel.PRIVATE)
@TableName("interaction_response")
public class InteractionResponse {
    @TableId(type = IdType.AUTO) private Long id;
    private Long interactionId;
    private Long sessionId;
    private String studentId;
    private String studentName;
    private String answer;
    private Boolean isCorrect;
    private Integer score;
    @TableField(fill = FieldFill.INSERT) private LocalDateTime respondedAt;
}
