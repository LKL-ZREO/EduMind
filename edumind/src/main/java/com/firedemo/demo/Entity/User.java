package com.firedemo.demo.Entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.firedemo.demo.common.util.AESEncryptHandler;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName(value = "sys_user", autoResultMap = true)
public class User {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    private String username;
    private String password;

    @TableField(typeHandler = AESEncryptHandler.class)
    private String phone;

    @TableField(typeHandler = AESEncryptHandler.class)
    private String email;

    private Long classId;

    private LocalDateTime createTime;
    private LocalDateTime updateTime;
    private Integer status;
}