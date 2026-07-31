package com.firedemo.edumind.shared.exception;

import lombok.Getter;
import org.springframework.http.HttpStatus;

/**
 * 业务异常
 */
@Getter
public class BusinessException extends RuntimeException {
    
    private final int code;
    private final HttpStatus httpStatus;
    
    public BusinessException(String message) {
        super(message);
        this.code = 500;
        this.httpStatus = HttpStatus.INTERNAL_SERVER_ERROR;
    }
    
    public BusinessException(int code, String message) {
        super(message);
        this.code = code;
        this.httpStatus = ErrorCode.httpStatusFor(code);
    }
    
    public BusinessException(ErrorCode errorCode) {
        super(errorCode.getMessage());
        this.code = errorCode.getCode();
        this.httpStatus = errorCode.getHttpStatus();
    }
}
