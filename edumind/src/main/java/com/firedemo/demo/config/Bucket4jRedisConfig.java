package com.firedemo.demo.config;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import io.github.bucket4j.distributed.ExpirationAfterWriteStrategy;
import io.github.bucket4j.distributed.proxy.ProxyManager;
import io.github.bucket4j.redis.redisson.cas.RedissonBasedProxyManager;
import lombok.extern.slf4j.Slf4j;
import org.redisson.command.CommandAsyncExecutor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

/**
 * Bucket4j 分布式令牌桶 — Redisson CAS 代理
 * <p>
 * 与现有 {@code RateLimitAspect}（方法级滑动窗口）分层协作：
 * <ul>
 *   <li>网关层：Bucket4j 令牌桶，粗粒度防刷（IP + URI 维度）</li>
 *   <li>方法层：Redis Lua 滑动窗口，细粒度业务限流</li>
 * </ul>
 */
@Slf4j
@Configuration
public class Bucket4jRedisConfig {

    /**
     * 分布式令牌桶代理管理器
     * <p>
     * 桶状态存储在 Redis，多实例共享同一限流状态。
     * 超期桶 60 分钟未读写则自动清理。
     */
    @Bean
    public ProxyManager<String> bucketProxyManager(CommandAsyncExecutor commandAsyncExecutor) {
        ProxyManager<String> proxyManager = RedissonBasedProxyManager
                .<String>builderFor(commandAsyncExecutor)
                .withExpirationStrategy(
                        ExpirationAfterWriteStrategy.basedOnTimeForRefillingBucketUpToMax(
                                Duration.ofMinutes(60)))
                .build();

        log.info("Bucket4j 分布式令牌桶代理已初始化（Redisson CAS）");
        return proxyManager;
    }

    /**
     * 本地缓存 — 减少 Redis 交互频率
     * <p>
     * 桶对象是代理引用，令牌状态仍在 Redis 端维护，
     * 本地缓存仅避免重复构造代理对象。
     */
    @Bean("bucketLocalCache")
    public Cache<String, io.github.bucket4j.Bucket> bucketLocalCache() {
        return Caffeine.newBuilder()
                .maximumSize(10_000)
                .expireAfterAccess(Duration.ofMinutes(10))
                .recordStats()
                .build();
    }
}
