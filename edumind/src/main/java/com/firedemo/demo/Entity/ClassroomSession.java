package com.firedemo.demo.Entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;

@Data @Builder @NoArgsConstructor @AllArgsConstructor(access = lombok.AccessLevel.PRIVATE)
@TableName("classroom_session")
public class ClassroomSession {
    @TableId(type = IdType.AUTO) private Long id;
    private Long classId;
    private Long teacherId;
    private String sessionCode;
    private String title;
    private Long courseId;
    private String status;
    private LocalDateTime startedAt;
    private LocalDateTime endedAt;
    @TableField(fill = FieldFill.INSERT) private LocalDateTime createdAt;
    @TableField(fill = FieldFill.INSERT_UPDATE) private LocalDateTime updatedAt;
}
