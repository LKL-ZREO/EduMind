package com.firedemo.edumind.homework;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("homework_draft")
public class HomeworkDraft {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long teacherId;

    private String taskName;

    private String description;

    private LocalDateTime deadline;

    private Boolean allowLate;

    private Integer latePenalty;

    private String status;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
