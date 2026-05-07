package com.firedemo.demo.common.exception;

import lombok.Getter;

/**
 * 错误码枚举
 */
@Getter
public enum ErrorCode {
    
    // 系统错误
    SUCCESS(200, "success"),
    SYSTEM_ERROR(500, "系统错误"),
    PARAM_ERROR(400, "参数错误"),
    
    // 认证授权
    UNAUTHORIZED(401, "未登录或登录已过期"),
    FORBIDDEN(403, "无权限访问"),
    
    // 用户相关
    USER_NOT_FOUND(1001, "用户不存在"),
    USER_ALREADY_EXISTS(1002, "用户名已存在"),
    PASSWORD_ERROR(1003, "密码错误"),
    ACCOUNT_DISABLED(1004, "账号已被禁用"),
    
    // 文件相关
    FILE_UPLOAD_ERROR(2001, "文件上传失败"),
    FILE_NOT_FOUND(2002, "文件不存在"),
    FILE_READ_ERROR(2003, "文件读取失败"),
    
    // AI服务
    AI_SERVICE_ERROR(3001, "AI服务暂时不可用"),
    AI_PARSE_ERROR(3002, "AI响应解析失败"),
    
    // 数据相关
    DATA_NOT_FOUND(4001, "数据不存在"),
    DATA_ALREADY_EXISTS(4002, "数据已存在");
    
    private final int code;
    private final String message;
    
    ErrorCode(int code, String message) {
        this.code = code;
        this.message = message;
    }
}
