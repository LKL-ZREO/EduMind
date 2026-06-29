package com.firedemo.demo.Entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 班级信息实体
 */
@Data
@TableName("class_info")
public class ClassInfo {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String name;

    private Long teacherId;

    private String description;

    private String qqGroupId;

    /** 所属课程（多个班级归入同一课程） */
    private String courseGroup;

    /** 所属课程ID（新） */
    private Long courseId;

    /** 6位邀请码 */
    private String inviteCode;

    /** ACTIVE / ARCHIVED */
    private String status;

    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
