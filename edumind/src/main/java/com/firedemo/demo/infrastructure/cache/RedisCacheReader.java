package com.firedemo.demo.infrastructure.cache;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RBucket;
import org.redisson.api.RedissonClient;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.function.Supplier;

/**
 * Redis 缓存读取器 — Cache-Aside 模式 + DB 兜底
 * <p>
 * 与 {@link CacheThroughService} 互补：
 * <ul>
 *   <li>CacheThroughService：Caffeine 本地缓存，重启丢失，用于高频热点数据</li>
 *   <li>RedisCacheReader：Redis 分布式缓存，重启不丢、多实例共享，用于会话状态等</li>
 * </ul>
 * <p>
 * 使用示例：
 * <pre>{@code
 *   Submission sub = redisCacheReader.read(
 *       "sub:" + submissionId,
 *       Duration.ofMinutes(5),
 *       () -> submissionMapper.selectById(submissionId)
 *   );
 * }</pre>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RedisCacheReader {

    private final RedissonClient redissonClient;

    /**
     * 先查 Redis，miss 则查 DB 并回写
     *
     * @param key      Redis key
     * @param ttl      缓存有效期
     * @param dbLoader DB 查询函数
     * @param <T>      返回值类型
     * @return 缓存或 DB 中的数据，DB 也查不到则返回 null
     */
    public <T> T read(String key, Duration ttl, Supplier<T> dbLoader) {
        RBucket<T> bucket = redissonClient.getBucket(key);
        T cached = bucket.get();
        if (cached != null) {
            log.debug("Redis 命中: {}", key);
            return cached;
        }

        T fromDb = dbLoader.get();
        if (fromDb != null) {
            bucket.set(fromDb, ttl);
            log.debug("Redis miss, DB 回写: key={}", key);
        }
        return fromDb;
    }

    /**
     * 主动删除缓存（写操作后调用）
     */
    public void evict(String key) {
        redissonClient.getBucket(key).delete();
    }
}
