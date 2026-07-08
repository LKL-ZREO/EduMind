package com.firedemo.demo.infrastructure.pdf;

/**
 * MinerU 解析异常（非受检，自动回退时上层不需要强制 catch）
 */
public class MineruException extends RuntimeException {

    public MineruException(String message) {
        super(message);
    }

    public MineruException(String message, Throwable cause) {
        super(message, cause);
    }
}
