package com.firedemo.demo.common.cache;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.stereotype.Service;

import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

/**
 * Cache-Aside 穿透防护服务
 * <p>
 * 缓存 Miss 时用分布式锁保证只有一个线程回源 DB，其他线程等锁释放后读缓存。
 * <pre>
 * Client → getOrLoad("class:metrics:1")
 *            ├─ Cache hit → 直接返回
 *            └─ Cache miss
 *                  ├─ 获取分布式锁 lock:cache:class:metrics:1
 *                  │   ├─ 拿到 → 查 DB → 写缓存 → 释放锁 → 返回
 *                  │   └─ 没拿到 → sleep(100ms) → 重试读缓存
 *                  └─ 重试 N 次仍 miss → 直接查 DB（兜底）
 * </pre>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CacheThroughService {

    private final CacheManager cacheManager;
    private final RedissonClient redissonClient;

    private static final String LOCK_PREFIX = "lock:cache:";
    private static final long LOCK_WAIT_MS = 100;
    private static final int MAX_RETRIES = 5;

    /**
     * Cache-Aside 读取（自动回源 + 防击穿）
     *
     * @param cacheName Caffeine 缓存名
     * @param key       缓存 key
     * @param loader    DB 回源函数
     * @param ttl       缓存 TTL（由 CacheTTL 工具类生成）
     * @param <T>       返回值类型
     */
    @SuppressWarnings("unchecked")
    public <T> T getOrLoad(String cacheName, String key, Supplier<T> loader, java.time.Duration ttl) {
        Cache cache = cacheManager.getCache(cacheName);
        if (cache == null) {
            log.warn("缓存不存在: {}, 直接查DB", cacheName);
            return loader.get();
        }

        // 1. 读缓存
        Cache.ValueWrapper wrapper = cache.get(key);
        if (wrapper != null) {
            log.debug("缓存命中: {}:{}", cacheName, key);
            return (T) wrapper.get();
        }

        // 2. 缓存 Miss → 分布式锁
        String lockKey = LOCK_PREFIX + cacheName + ":" + key;
        RLock lock = redissonClient.getLock(lockKey);

        try {
            if (lock.tryLock(LOCK_WAIT_MS, 30_000, TimeUnit.MILLISECONDS)) {
                try {
                    // 双重检查
                    wrapper = cache.get(key);
                    if (wrapper != null) {
                        return (T) wrapper.get();
                    }

                    // 回源 DB
                    T data = loader.get();
                    if (data != null) {
                        cache.put(key, data);
                        log.debug("缓存回源 + 写入: {}:{}, ttl={}", cacheName, key, ttl);
                    }
                    return data;
                } finally {
                    lock.unlock();
                }
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        // 3. 没拿到锁 → 等一等等别人写完缓存
        for (int i = 0; i < MAX_RETRIES; i++) {
            try {
                Thread.sleep(LOCK_WAIT_MS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
            wrapper = cache.get(key);
            if (wrapper != null) {
                log.debug("等待后缓存命中(第{}次): {}:{}", i + 1, cacheName, key);
                return (T) wrapper.get();
            }
        }

        // 4. 兜底：直接查 DB
        log.debug("等待超时，直接查DB: {}:{}", cacheName, key);
        return loader.get();
    }
}
