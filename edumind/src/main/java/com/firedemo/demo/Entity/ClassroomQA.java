package com.firedemo.demo.Entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;

@Data @Builder @NoArgsConstructor @AllArgsConstructor(access = lombok.AccessLevel.PRIVATE)
@TableName("classroom_qa")
public class ClassroomQA {
    @TableId(type = IdType.AUTO) private Long id;
    private Long sessionId;
    private String question;
    private String studentId;
    private String studentName;
    private Boolean isAnswered;
    private String answerText;
    private Long similarTo;
    private Integer similarCount;
    @TableField(fill = FieldFill.INSERT) private LocalDateTime createdAt;
}
