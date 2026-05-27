package com.firedemo.demo.Entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 教师自定义热力图知识点
 */
@Data
@TableName("teacher_knowledge")
public class TeacherKnowledge {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long classId;

    private String name;

    private String color;

    private Integer sortOrder;

    private Long createdBy;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
