package com.firedemo.demo.common.result;

import com.firedemo.demo.common.exception.ErrorCode;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;

import java.time.Instant;

/**
 * 统一返回结果
 */
@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class Result<T> {

    private Integer code;
    private String message;
    private T data;

    /** 请求追踪 ID，仅在错误响应中出现，方便用户反馈问题时定位 */
    private String requestId;

    /** 响应时间戳，仅在错误响应中出现 */
    private Long timestamp;

    public static <T> Result<T> success(T data) {
        Result<T> result = new Result<>();
        result.setCode(200);
        result.setMessage("success");
        result.setData(data);
        return result;
    }

    public static <T> Result<T> success() {
        return success(null);
    }

    public static <T> Result<T> error(String message) {
        return error(500, message);
    }

    public static <T> Result<T> error(int code, String message) {
        Result<T> result = new Result<>();
        result.setCode(code);
        result.setMessage(message);
        result.setTimestamp(Instant.now().toEpochMilli());
        return result;
    }

    /** 带 requestId 的错误响应 — 用户反馈时引用此 ID */
    public static <T> Result<T> error(int code, String message, String requestId) {
        Result<T> result = error(code, message);
        result.setRequestId(requestId);
        return result;
    }

    /** 带数据的错误响应（如校验不匹配警告） */
    public static <T> Result<T> error(int code, String message, T data) {
        Result<T> result = error(code, message);
        result.setData(data);
        return result;
    }

    /** 使用 ErrorCode 枚举构建错误响应 */
    public static <T> Result<T> error(ErrorCode errorCode) {
        return error(errorCode.getCode(), errorCode.getMessage());
    }

    /** 使用 ErrorCode 枚举 + requestId */
    public static <T> Result<T> error(ErrorCode errorCode, String requestId) {
        return error(errorCode.getCode(), errorCode.getMessage(), requestId);
    }

    public boolean isSuccess() {
        return code != null && code == 200;
    }
}
