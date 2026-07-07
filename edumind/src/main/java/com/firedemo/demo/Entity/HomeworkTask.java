package com.firedemo.demo.Entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 作业任务表
 */
@Data
@TableName("homework_task")
public class HomeworkTask {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long classId;

    private String taskName;

    private String description;

    private LocalDateTime deadline;

    private Boolean allowLate;

    private Integer latePenalty;

    private String status;

    private Long createdBy;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
