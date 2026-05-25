package com.firedemo.demo.common.limiter;

import com.github.benmanes.caffeine.cache.Cache;
import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.BucketConfiguration;
import io.github.bucket4j.Refill;
import io.github.bucket4j.distributed.proxy.ProxyManager;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * 分布式令牌桶限流器
 * <p>
 * 桶状态持久化在 Redis，多实例共享限流状态。
 * 本地 Caffeine 缓存桶代理引用，减少 Redis 交互。
 */
@Slf4j
@Component
public class DistributedRateLimiter {

    private final ProxyManager<String> proxyManager;
    private final Cache<String, Bucket> localCache;

    public DistributedRateLimiter(ProxyManager<String> proxyManager,
                                  @Qualifier("bucketLocalCache") Cache<String, Bucket> localCache) {
        this.proxyManager = proxyManager;
        this.localCache = localCache;
    }

    /**
     * 尝试消费 1 个令牌
     *
     * @param key             限流键（如 bucket:{ip}:/api/xxx）
     * @param capacity        桶容量（允许的最大突发请求数）
     * @param refillPerMinute 每分钟补充令牌数（稳态速率）
     * @return true=放行, false=拒绝
     */
    public boolean tryConsume(String key, int capacity, long refillPerMinute) {
        return tryConsume(key, capacity, refillPerMinute, 1);
    }

    /**
     * 尝试消费指定数量令牌
     */
    public boolean tryConsume(String key, int capacity, long refillPerMinute, int tokens) {
        Bucket bucket = resolveBucket(key, capacity, refillPerMinute);
        return bucket.tryConsume(tokens);
    }

    /**
     * 获取桶当前可用令牌数（供监控端点暴露）
     */
    public long getAvailableTokens(String key, int capacity, long refillPerMinute) {
        return resolveBucket(key, capacity, refillPerMinute).getAvailableTokens();
    }

    // ==================== 内部实现 ====================

    /**
     * 解析桶：Caffeine 本地缓存桶代理引用，miss 时通过 ProxyManager 构建分布式桶
     */
    private Bucket resolveBucket(String key, int capacity, long refillPerMinute) {
        // 参数变化时需重建桶，因此将参数编入缓存键
        String cacheKey = key + "|" + capacity + "|" + refillPerMinute;
        return localCache.get(cacheKey, k -> buildDistributedBucket(key, capacity, refillPerMinute));
    }

    /**
     * 通过 ProxyManager 构建分布式令牌桶
     * <p>
     * proxyManager.builder().withKey(key).build(configuration) 返回 BucketProxy，
     * 其 tryConsume 操作通过 Redis Lua 脚本原子执行。
     */
    private Bucket buildDistributedBucket(String key, int capacity, long refillPerMinute) {
        // greedy: 令牌连续平滑补充（非梯级补充，更符合真实流量模型）
        Refill refill = Refill.greedy(refillPerMinute, Duration.ofMinutes(1));
        Bandwidth limit = Bandwidth.classic(capacity, refill);
        BucketConfiguration configuration = BucketConfiguration.builder()
                .addLimit(limit)
                .build();

        return proxyManager.builder()
                .build(key, configuration);
    }
}
