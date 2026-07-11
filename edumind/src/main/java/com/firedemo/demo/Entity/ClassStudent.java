package com.firedemo.demo.Entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.firedemo.demo.common.util.AESEncryptHandler;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 班级学生关系实体
 */
@Data
@TableName(value = "class_student", autoResultMap = true)
public class ClassStudent {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long classId;

    private String studentId;

    @TableField(typeHandler = AESEncryptHandler.class)
    private String studentName;

    private String source; // 'auto'=自动收集, 'manual'=手动导入

    private LocalDateTime createdAt;
}
