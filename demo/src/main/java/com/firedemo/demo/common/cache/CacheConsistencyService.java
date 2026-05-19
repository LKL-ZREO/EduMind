package com.firedemo.demo.common.cache;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

/**
 * 缓存一致性服务 — Cache Aside 模式 + 延迟双删
 * <p>
 * 写操作流程:
 * 1. 删除缓存
 * 2. 更新数据库
 * 3. 延迟 500ms 二次删除缓存（防止并发读到旧值后写回）
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CacheConsistencyService {

    private final CacheManager cacheManager;

    /**
     * 同步删除 + 异步延迟二次删除（延迟双删）
     *
     * @param cacheName 缓存名（对应 @CacheConfig 或 Caffeine cache 名）
     * @param keys      缓存 key 列表
     */
    public void evictWithDoubleDelete(String cacheName, String... keys) {
        Cache cache = cacheManager.getCache(cacheName);
        if (cache == null) {
            return;
        }
        // 第一次删除
        for (String key : keys) {
            cache.evict(key);
        }
        log.debug("缓存第一删完成: cacheName={}, keys={}", cacheName, keys);
        // 异步延迟第二次删除
        delayedEvict(cacheName, keys);
    }

    @Async
    public void delayedEvict(String cacheName, String... keys) {
        try {
            Thread.sleep(500);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return;
        }
        Cache cache = cacheManager.getCache(cacheName);
        if (cache == null) {
            return;
        }
        for (String key : keys) {
            cache.evict(key);
        }
        log.debug("缓存延迟双删完成: cacheName={}, keys={}", cacheName, keys);
    }
}
