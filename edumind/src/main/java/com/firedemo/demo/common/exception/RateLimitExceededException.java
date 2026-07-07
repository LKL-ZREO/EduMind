package com.firedemo.demo.common.exception;

import com.firedemo.demo.common.exception.ErrorCode;

/**
 * 限流异常
 */
public class RateLimitExceededException extends BusinessException {

    public RateLimitExceededException(String message) {
        super(ErrorCode.RATE_LIMIT_EXCEEDED.getCode(), message);
    }
}
