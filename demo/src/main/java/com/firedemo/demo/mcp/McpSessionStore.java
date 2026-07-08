package com.firedemo.demo.mcp;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RBucket;
import org.redisson.api.RedissonClient;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Set;

/**
 * Redis 存储：sessionId → 用户上下文。
 * 聊天发起前存入，MCP 工具回调时/构建 Prompt 时取出。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class McpSessionStore {

    private final RedissonClient redissonClient;

    private static final String PREFIX = "mcp:ctx:";
    private static final Duration TTL = Duration.ofMinutes(10);

    /** 格式: userId|kb1,kb2,...|courseId */
    public void put(String sessionId, Long userId, Set<Long> accessibleKbIds, Long courseId) {
        StringBuilder sb = new StringBuilder();
        sb.append(userId).append("|");
        if (accessibleKbIds != null && !accessibleKbIds.isEmpty()) {
            sb.append(String.join(",", accessibleKbIds.stream().map(String::valueOf).toList()));
        }
        sb.append("|");
        if (courseId != null) {
            sb.append(courseId);
        }
        RBucket<String> bucket = redissonClient.getBucket(PREFIX + sessionId);
        bucket.set(sb.toString(), TTL);
        log.debug("MCP session stored: sessionId={}, userId={}, kbCount={}, courseId={}",
                sessionId, userId, accessibleKbIds != null ? accessibleKbIds.size() : 0, courseId);
    }

    /** 兼容旧调用（无 courseId） */
    public void put(String sessionId, Long userId, Set<Long> accessibleKbIds) {
        put(sessionId, userId, accessibleKbIds, null);
    }

    public ToolContext get(String sessionId) {
        if (sessionId == null || sessionId.isEmpty()) return null;
        RBucket<String> bucket = redissonClient.getBucket(PREFIX + sessionId);
        String data = bucket.get();
        if (data == null) return null;
        String[] parts = data.split("\\|", 3);
        Long userId = Long.parseLong(parts[0]);
        Set<Long> kbIds = parts.length > 1 && !parts[1].isEmpty()
                ? Set.of(parts[1].split(",")).stream().map(Long::valueOf).collect(java.util.stream.Collectors.toSet())
                : Set.of();
        Long courseId = parts.length > 2 && !parts[2].isEmpty()
                ? Long.parseLong(parts[2]) : null;
        log.debug("MCP session resolved: sessionId={}, userId={}, kbCount={}, courseId={}",
                sessionId, userId, kbIds.size(), courseId);
        return new ToolContext(userId, kbIds, courseId);
    }
}
