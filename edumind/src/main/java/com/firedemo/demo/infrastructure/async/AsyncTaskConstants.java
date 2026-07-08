package com.firedemo.demo.infrastructure.async;

/**
 * 异步批改任务 Redis Stream 常量
 */
public final class AsyncTaskConstants {

    private AsyncTaskConstants() {
    }

    /** 批改任务 Stream Key */
    public static final String GRADING_STREAM_KEY = "homework:grading:stream";

    /** 消费者组名 */
    public static final String GRADING_GROUP = "grading-group";

    /** 消费者名称前缀 */
    public static final String GRADING_CONSUMER_PREFIX = "grading-consumer-";

    /** 最大重试次数 */
    public static final int MAX_RETRY = 3;

    /** Stream 最大长度（自动裁剪旧消息） */
    public static final int STREAM_MAX_LEN = 1000;

    /** 每次拉取消息数 */
    public static final int BATCH_SIZE = 1;

    /** 消费者阻塞等待超时（毫秒） */
    public static final long POLL_TIMEOUT_MS = 5000;

    /** 消息字段：提交ID */
    public static final String FIELD_SUBMISSION_ID = "submissionId";

    /** 消息字段：作业要求 */
    public static final String FIELD_REQUIREMENT = "requirement";

    /** 消息字段：重试次数 */
    public static final String FIELD_RETRY_COUNT = "retryCount";
}
