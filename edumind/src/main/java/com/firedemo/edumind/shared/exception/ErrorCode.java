package com.firedemo.edumind.shared.exception;

import lombok.Getter;
import org.springframework.http.HttpStatus;

import java.util.Arrays;

/**
 * 错误码枚举
 */
@Getter
public enum ErrorCode {
    
    // 系统错误
    SUCCESS(200, "success", HttpStatus.OK),
    SYSTEM_ERROR(500, "系统错误", HttpStatus.INTERNAL_SERVER_ERROR),
    PARAM_ERROR(400, "参数错误", HttpStatus.BAD_REQUEST),
    CONFIRMATION_REQUIRED(300, "需要用户确认", HttpStatus.CONFLICT),
    PRECONDITION_REQUIRED(428, "前置条件未满足", HttpStatus.PRECONDITION_REQUIRED),
    
    // 限流
    RATE_LIMIT_EXCEEDED(429, "请求过于频繁", HttpStatus.TOO_MANY_REQUESTS),

    // 认证授权
    UNAUTHORIZED(401, "未登录或登录已过期", HttpStatus.UNAUTHORIZED),
    FORBIDDEN(403, "无权限访问", HttpStatus.FORBIDDEN),
    
    // 用户相关
    USER_NOT_FOUND(1001, "用户不存在", HttpStatus.UNAUTHORIZED),
    USER_ALREADY_EXISTS(1002, "用户名已存在", HttpStatus.CONFLICT),
    PASSWORD_ERROR(1003, "密码错误", HttpStatus.UNAUTHORIZED),
    ACCOUNT_DISABLED(1004, "账号已被禁用", HttpStatus.FORBIDDEN),
    
    // 文件相关
    FILE_UPLOAD_ERROR(2001, "文件上传失败", HttpStatus.INTERNAL_SERVER_ERROR),
    FILE_NOT_FOUND(2002, "文件不存在", HttpStatus.NOT_FOUND),
    FILE_READ_ERROR(2003, "文件读取失败", HttpStatus.INTERNAL_SERVER_ERROR),
    
    // AI服务
    AI_SERVICE_ERROR(3001, "AI服务暂时不可用", HttpStatus.SERVICE_UNAVAILABLE),
    AI_PARSE_ERROR(3002, "AI响应解析失败", HttpStatus.BAD_GATEWAY),
    
    // 数据相关
    DATA_NOT_FOUND(4001, "数据不存在", HttpStatus.NOT_FOUND),
    DATA_ALREADY_EXISTS(4002, "数据已存在", HttpStatus.CONFLICT);
    
    private final int code;
    private final String message;
    private final HttpStatus httpStatus;
    
    ErrorCode(int code, String message, HttpStatus httpStatus) {
        this.code = code;
        this.message = message;
        this.httpStatus = httpStatus;
    }

    public static HttpStatus httpStatusFor(int code) {
        return Arrays.stream(values())
                .filter(errorCode -> errorCode.code == code)
                .map(ErrorCode::getHttpStatus)
                .findFirst()
                .orElseGet(() -> {
                    HttpStatus standard = HttpStatus.resolve(code);
                    return standard != null && standard.isError()
                            ? standard
                            : HttpStatus.INTERNAL_SERVER_ERROR;
                });
    }
}
