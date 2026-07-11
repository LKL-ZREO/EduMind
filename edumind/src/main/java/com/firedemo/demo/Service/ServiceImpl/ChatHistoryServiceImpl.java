package com.firedemo.demo.Service.ServiceImpl;


import com.firedemo.demo.Entity.ChatHistory;
import com.firedemo.demo.Service.ChatHistoryService;
import com.firedemo.demo.infrastructure.cache.CacheThroughService;
import com.firedemo.demo.mapper.ChatHistoryMapper;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.CacheManager;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 对话记录服务实现
 *
 * @author 海克斯
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ChatHistoryServiceImpl implements ChatHistoryService {

    private final ChatHistoryMapper chatHistoryMapper;
    private final CacheThroughService cacheThroughService;
    private final CacheManager cacheManager;

    private static final String CACHE_NAME = "chatHistory";
    private static final Duration CACHE_TTL = Duration.ofMinutes(5);

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean save(ChatHistory history) {
        boolean result = chatHistoryMapper.insert(history) > 0;
        if (result && history.getUserId() != null) {
            evictHistoryCache(history.getUserId());
        }
        return result;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean saveBatch(List<ChatHistory> histories) {
        if (histories == null || histories.isEmpty()) return true;
        // 批量插入替代逐条 INSERT，减少 DB 往返
        int size = histories.size();
        int batchSize = 100;
        for (int i = 0; i < size; i += batchSize) {
            List<ChatHistory> batch = histories.subList(i, Math.min(size, i + batchSize));
            chatHistoryMapper.insertBatch(batch);
        }
        return true;
    }

    @Override
    public List<ChatHistory> getHistory(String sessionId, int limit) {
        return chatHistoryMapper.selectBySessionId(sessionId, limit);
    }

    @Override
    public List<String> getUserSessions(Long userId) {
        return chatHistoryMapper.selectSessionIdsByUserId(userId);
    }

    @Override
    public List<ChatHistory> getUserHistory(Long userId) {
        return cacheThroughService.getOrLoad(CACHE_NAME, "history:" + userId,
                () -> chatHistoryMapper.selectByUserId(userId), CACHE_TTL);
    }

    @Override
    public String buildContextPrompt(String sessionId, int limit) {
        List<ChatHistory> histories = getHistory(sessionId, limit);
        if (histories.isEmpty()) {
            return "";
        }

        return histories.stream()
                .map(h -> h.getRole() + ": " + h.getContent())
                .collect(Collectors.joining("\n"));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean deleteByUserId(Long userId) {
        int deleted = chatHistoryMapper.deleteByUserId(userId);
        evictHistoryCache(userId);
        log.info("已删除用户 {} 的 {} 条对话记录", userId, deleted);
        return deleted >= 0;
    }

    private void evictHistoryCache(Long userId) {
        var cache = cacheManager.getCache(CACHE_NAME);
        if (cache != null) {
            cache.evict("history:" + userId);
        }
    }
}
