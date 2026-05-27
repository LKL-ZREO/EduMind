package com.firedemo.demo.Service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RBucket;
import org.redisson.api.RedissonClient;
import org.springframework.stereotype.Service;

import java.time.Duration;

/**
 * 活跃教师跟踪 — Redis 存储当前前端登录的教师 userId
 * <p>
 * OneBot RAG 检索时以此为身份，检索该教师的私有库 + 共享库。
 * TTL 10 分钟，教师每次操作自动续期。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ActiveTeacherService {

    private final RedissonClient redissonClient;

    private static final String KEY = "rag:active_teacher";
    private static final Duration TTL = Duration.ofMinutes(10);

    /**
     * 更新活跃教师（前端老师操作时调用）
     */
    public void touch(Long userId) {
        if (userId == null) {
            return;
        }
        RBucket<Long> bucket = redissonClient.getBucket(KEY);
        bucket.set(userId, TTL);
        log.debug("活跃教师已更新: userId={}", userId);
    }

    /**
     * 获取当前活跃教师 userId
     *
     * @return userId，无活跃教师时返回 null
     */
    public Long getActiveUserId() {
        RBucket<Long> bucket = redissonClient.getBucket(KEY);
        Long userId = bucket.get();
        if (userId != null) {
            log.debug("获取活跃教师: userId={}", userId);
        }
        return userId;
    }
}
