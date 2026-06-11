package com.firedemo.demo.common.enums;

import lombok.Getter;

/**
 * 作业提交状态枚举
 */
@Getter
public enum SubmissionStatus {

    /** 排队中，等待批改 */
    PENDING("PENDING", "排队中"),

    /** 正在批改 */
    PROCESSING("PROCESSING", "批改中"),

    /** 批改完成 */
    COMPLETED("COMPLETED", "已完成"),

    /** 批改失败 */
    FAILED("FAILED", "失败");

    private final String code;
    private final String desc;

    SubmissionStatus(String code, String desc) {
        this.code = code;
        this.desc = desc;
    }

    /** 是否终态（不会再变化） */
    public boolean isFinal() {
        return this == COMPLETED || this == FAILED;
    }

    /** 判断某个状态值是否为终态 */
    public static boolean isFinal(String statusCode) {
        return COMPLETED.code.equals(statusCode) || FAILED.code.equals(statusCode);
    }
}
