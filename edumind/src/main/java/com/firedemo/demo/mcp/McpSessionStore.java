package com.firedemo.demo.mcp;

import com.firedemo.demo.agent.context.AgentChannel;
import com.firedemo.demo.agent.context.AgentExecutionContext;
import com.firedemo.demo.agent.context.AgentExecutionContextFactory;
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
    private final AgentExecutionContextFactory executionContextFactory;

    private static final String PREFIX = "mcp:ctx:";
    private static final Duration TTL = Duration.ofMinutes(10);

    /** 格式: userId|kb1,kb2,...|courseId */
    public void put(AgentExecutionContext context) {
        if (context == null || !context.isAuthenticated()) {
            return;
        }
        StringBuilder sb = new StringBuilder();
        sb.append(context.userId()).append("|");
        Set<Long> accessibleKbIds = context.accessibleKbIds();
        if (accessibleKbIds != null && !accessibleKbIds.isEmpty()) {
            sb.append(String.join(",", accessibleKbIds.stream().map(String::valueOf).toList()));
        }
        sb.append("|");
        if (context.courseId() != null) {
            sb.append(context.courseId());
        }
        RBucket<String> bucket = redissonClient.getBucket(PREFIX + context.sessionId());
        bucket.set(sb.toString(), TTL);
        log.debug("MCP session stored: sessionId={}, userId={}, kbCount={}, courseId={}",
                context.sessionId(), context.userId(), accessibleKbIds.size(), context.courseId());
    }

    public AgentExecutionContext resolve(String sessionId, AgentChannel channel) {
        StoredSession stored = read(sessionId);
        if (stored == null) {
            return null;
        }
        return executionContextFactory.create(
                sessionId,
                stored.userId(),
                stored.courseId(),
                stored.accessibleKbIds(),
                channel);
    }

    public Long getCourseId(String sessionId) {
        StoredSession stored = read(sessionId);
        return stored != null ? stored.courseId() : null;
    }

    private StoredSession read(String sessionId) {
        if (sessionId == null || sessionId.isEmpty()) return null;
        RBucket<String> bucket = redissonClient.getBucket(PREFIX + sessionId);
        String data = bucket.get();
        if (data == null) return null;
        try {
            String[] parts = data.split("\\|", 3);
            Long userId = Long.parseLong(parts[0]);
            Set<Long> kbIds = parts.length > 1 && !parts[1].isEmpty()
                    ? java.util.Arrays.stream(parts[1].split(","))
                            .filter(s -> !s.isEmpty())
                            .map(Long::valueOf)
                            .collect(java.util.stream.Collectors.toSet())
                    : Set.of();
            Long courseId = parts.length > 2 && !parts[2].isEmpty()
                    ? Long.parseLong(parts[2]) : null;
            log.debug("MCP session resolved: sessionId={}, userId={}, kbCount={}, courseId={}",
                    sessionId, userId, kbIds.size(), courseId);
            return new StoredSession(userId, kbIds, courseId);
        } catch (RuntimeException e) {
            log.warn("Invalid MCP session data: sessionId={}", sessionId);
            return null;
        }
    }

    private record StoredSession(Long userId, Set<Long> accessibleKbIds, Long courseId) {
    }
}
