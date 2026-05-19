package com.firedemo.demo.common.async;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RStream;
import org.redisson.api.RedissonClient;
import org.redisson.client.codec.StringCodec;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Redis Stream 积压监控 — 定时上报 Stream 队列状态
 * <p>
 * 当待消费消息或 Pending 消息堆积超过阈值时告警日志，
 * 后续接入 Prometheus 后可替换为 Gauge 指标暴露。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class StreamMonitor {

    private final RedissonClient redissonClient;

    /** 待消费消息堆积告警阈值 */
    private static final long TOTAL_ALERT_THRESHOLD = 50;

    @Scheduled(fixedDelay = 15_000)
    public void reportStreamMetrics() {
        try {
            RStream<String, String> stream = redissonClient.getStream(
                    AsyncTaskConstants.GRADING_STREAM_KEY, StringCodec.INSTANCE);

            long totalLen = stream.size();

            if (totalLen >= TOTAL_ALERT_THRESHOLD) {
                log.warn("Stream积压告警 — 待消费={}, 消费者组={}",
                        totalLen, AsyncTaskConstants.GRADING_GROUP);
            }
        } catch (Exception e) {
            log.error("Stream监控异常", e);
        }
    }
}
