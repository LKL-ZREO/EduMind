package com.firedemo.demo.Entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("chat_session")
public class ChatSession {

    @TableId(type = IdType.INPUT)
    private String sessionId;
    private Long userId;
    private String title;
    private Long classId;
    private Long courseId;
    private String selectedKbIds;
    private String mode;
    private Boolean pinned;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
