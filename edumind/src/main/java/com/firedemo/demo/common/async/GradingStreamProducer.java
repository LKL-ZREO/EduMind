package com.firedemo.demo.common.async;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RStream;
import org.redisson.api.RedissonClient;
import org.redisson.api.stream.StreamAddArgs;
import org.redisson.api.stream.StreamTrimArgs;
import org.redisson.client.codec.StringCodec;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * 批改任务生产者 — 将作业提交推入 Redis Stream 队列
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class GradingStreamProducer {

    private final RedissonClient redissonClient;

    /**
     * 发送批改任务到 Stream
     *
     * @param submissionId 提交记录ID
     * @param requirement  作业要求
     */
    public void sendTask(Long submissionId, String requirement) {
        RStream<String, String> stream = redissonClient.getStream(
                AsyncTaskConstants.GRADING_STREAM_KEY, StringCodec.INSTANCE);

        Map<String, String> message = Map.of(
                AsyncTaskConstants.FIELD_SUBMISSION_ID, submissionId.toString(),
                AsyncTaskConstants.FIELD_REQUIREMENT, requirement != null ? requirement : "",
                AsyncTaskConstants.FIELD_RETRY_COUNT, "0"
        );

        stream.add(StreamAddArgs.entries(message));
        // 限制 Stream 长度，防止 Redis 内存无限增长
        stream.trimNonStrict(StreamTrimArgs.maxLen(AsyncTaskConstants.STREAM_MAX_LEN).noLimit());

        log.info("批改任务已入队: submissionId={}", submissionId);
    }

    /**
     * 重试任务（重新入队）
     */
    public void retryTask(Long submissionId, String requirement, int retryCount) {
        RStream<String, String> stream = redissonClient.getStream(
                AsyncTaskConstants.GRADING_STREAM_KEY, StringCodec.INSTANCE);

        Map<String, String> message = Map.of(
                AsyncTaskConstants.FIELD_SUBMISSION_ID, submissionId.toString(),
                AsyncTaskConstants.FIELD_REQUIREMENT, requirement != null ? requirement : "",
                AsyncTaskConstants.FIELD_RETRY_COUNT, String.valueOf(retryCount)
        );

        stream.add(StreamAddArgs.entries(message));
        stream.trimNonStrict(StreamTrimArgs.maxLen(AsyncTaskConstants.STREAM_MAX_LEN).noLimit());

        log.info("批改任务重试入队: submissionId={}, retryCount={}", submissionId, retryCount);
    }
}
