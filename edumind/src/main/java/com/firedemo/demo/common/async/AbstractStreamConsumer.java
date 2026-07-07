package com.firedemo.demo.common.async;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.redisson.RedissonShutdownException;
import org.redisson.api.RStream;
import org.redisson.api.RedissonClient;
import org.redisson.api.StreamMessageId;
import org.redisson.api.stream.StreamCreateGroupArgs;
import org.redisson.api.stream.StreamReadGroupArgs;
import org.redisson.client.codec.StringCodec;
import org.springframework.scheduling.annotation.Scheduled;

import java.time.Duration;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Redis Stream 消费者模板基类 — 消费循环 / ACK / 重试 / Pending 认领 收敛于此
 * <p>
 * 子类只需关注 10 个钩子方法，零框架重复代码。
 * <p>
 * 使用示例：
 * <pre>{@code
 * @Component
 * public class MyConsumer extends AbstractStreamConsumer {
 *     public MyConsumer(RedissonClient rc) { super(rc); }
 *
 *     @Override protected String streamKey() { return "my:stream"; }
 *     @Override protected String groupName() { return "my-group"; }
 *     @Override protected void processOne(StreamMessageId msgId, Map<String,String> data) {
 *         // 业务逻辑
 *     }
 *     // ... 其余 7 个钩子
 * }
 * }</pre>
 */
@Slf4j
public abstract class AbstractStreamConsumer {

    private final RedissonClient redissonClient;
    private final AtomicBoolean running = new AtomicBoolean(false);
    private ExecutorService executor;
    private String consumerName;

    protected AbstractStreamConsumer(RedissonClient redissonClient) {
        this.redissonClient = redissonClient;
    }

    // ==================== 生命周期 ====================

    @PostConstruct
    public void init() {
        this.consumerName = consumerPrefix() + "-" + UUID.randomUUID().toString().substring(0, 8);

        RStream<String, String> stream = redissonClient.getStream(streamKey(), StringCodec.INSTANCE);
        try {
            stream.createGroup(StreamCreateGroupArgs.name(groupName()).makeStream());
            log.info("{} 消费者组已创建: {}", taskName(), groupName());
        } catch (Exception e) {
            log.info("{} 消费者组已存在，跳过创建", taskName());
        }

        this.executor = Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, threadName());
            t.setDaemon(true);
            return t;
        });

        running.set(true);
        executor.submit(this::consumeLoop);
        log.info("{} 消费者已启动: consumerName={}", taskName(), consumerName);
    }

    @PreDestroy
    public void shutdown() {
        running.set(false);
        if (executor != null) {
            executor.shutdown();
        }
        log.info("{} 消费者已关闭: consumerName={}", taskName(), consumerName);
    }

    // ==================== 消费循环（固定模板，子类不动） ====================

    private void consumeLoop() {
        RStream<String, String> stream = redissonClient.getStream(streamKey(), StringCodec.INSTANCE);

        while (running.get()) {
            try {
                Map<StreamMessageId, Map<String, String>> messages = stream.readGroup(
                        groupName(),
                        consumerName,
                        StreamReadGroupArgs.neverDelivered()
                                .count(batchSize())
                                .timeout(Duration.ofMillis(pollTimeoutMs()))
                );

                if (messages == null || messages.isEmpty()) {
                    continue;
                }

                for (Map.Entry<StreamMessageId, Map<String, String>> entry : messages.entrySet()) {
                    processOne(entry.getKey(), entry.getValue());
                }
            } catch (RedissonShutdownException e) {
                log.info("Redisson 已关闭，{} 消费者退出", taskName());
                break;
            } catch (Exception e) {
                if (Thread.currentThread().isInterrupted()) {
                    log.info("{} 消费者线程被中断", taskName());
                    break;
                }
                log.error("{} 消费异常", taskName(), e);
                // 退避 1 秒，避免 Redis 故障时 100% CPU 自旋 + 日志风暴
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        }
    }

    // ==================== Pending 僵尸消息认领（固定模板） ====================

    @Scheduled(fixedDelay = 30_000)
    @SuppressWarnings("unchecked")
    public void claimPendingMessages() {
        try {
            RStream<String, String> stream = redissonClient.getStream(streamKey(), StringCodec.INSTANCE);
            java.util.List rawList = (java.util.List) stream.listPending(
                    groupName(),
                    StreamMessageId.MIN,
                    StreamMessageId.MAX,
                    100);

            if (rawList == null || rawList.isEmpty()) {
                return;
            }

            int claimedCount = 0;
            for (Object item : rawList) {
                try {
                    StreamMessageId msgId = extractMessageId(item);
                    long idleTime = extractIdleTime(item);
                    if (idleTime > pendingIdleThresholdMs()) {
                        Map<StreamMessageId, Map<String, String>> claimed = stream.claim(
                                groupName(), this.consumerName,
                                idleTime, TimeUnit.MILLISECONDS, msgId);

                        if (claimed != null && !claimed.isEmpty()) {
                            for (Map.Entry<StreamMessageId, Map<String, String>> entry : claimed.entrySet()) {
                                processOne(entry.getKey(), entry.getValue());
                                claimedCount++;
                            }
                        }
                    }
                } catch (Exception e) {
                    log.warn("{} 僵尸消息扫描失败，跳过本轮: {}", taskName(), e.getMessage());
                }
            }
            if (claimedCount > 0) {
                log.info("{} 认领并处理僵尸消息: count={}", taskName(), claimedCount);
            }
        } catch (Exception e) {
            log.debug("{} Pending扫描结束: {}", taskName(), e.getMessage());
        }
    }

    // ==================== 辅助方法 ====================

    /** 确认消息 */
    protected void ack(StreamMessageId messageId) {
        try {
            RStream<String, String> stream = redissonClient.getStream(streamKey(), StringCodec.INSTANCE);
            stream.ack(groupName(), messageId);
        } catch (Exception e) {
            log.error("{} ACK失败: messageId={}", taskName(), messageId, e);
        }
    }

    /** 解析重试次数 */
    protected int parseRetryCount(Map<String, String> data) {
        try {
            return Integer.parseInt(data.getOrDefault("retryCount", "0"));
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    protected String truncate(String text, int maxLen) {
        if (text == null) return null;
        return text.length() > maxLen ? text.substring(0, maxLen) : text;
    }

    protected RedissonClient redisson() {
        return redissonClient;
    }

    protected String getConsumerName() {
        return consumerName;
    }

    // ==================== Pending 反射辅助 ====================

    private StreamMessageId extractMessageId(Object entry) throws Exception {
        if (entry instanceof StreamMessageId sid) return sid;
        return (StreamMessageId) entry.getClass().getMethod("getId").invoke(entry);
    }

    private long extractIdleTime(Object entry) throws Exception {
        if (entry instanceof StreamMessageId) return Long.MAX_VALUE;
        return (long) entry.getClass().getMethod("getIdleTime").invoke(entry);
    }

    // ==================== 子类必须实现的钩子方法 ====================

    /** 任务展示名称（日志用），如 "批改任务" */
    protected abstract String taskName();

    /** Redis Stream Key */
    protected abstract String streamKey();

    /** 消费者组名 */
    protected abstract String groupName();

    /** 消费者名前缀 */
    protected abstract String consumerPrefix();

    /** 守护线程名 */
    protected abstract String threadName();

    /** 每批拉取消息数 */
    protected abstract int batchSize();

    /** 轮询超时（毫秒） */
    protected abstract long pollTimeoutMs();

    /** Pending 消息空闲多久后认领（毫秒），默认 5 分钟 */
    protected long pendingIdleThresholdMs() {
        return 300_000;
    }

    /**
     * 处理单条消息 — 子类唯一需要实现的业务入口
     * <p>
     * 子类在此方法中实现完整业务逻辑。
     * 重试逻辑由子类自行控制（调用 ack() 后重新入队或标记失败）。
     */
    protected abstract void processOne(StreamMessageId messageId, Map<String, String> data);
}
