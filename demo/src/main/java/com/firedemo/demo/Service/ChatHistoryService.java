package com.firedemo.demo.Service;

import com.firedemo.demo.Entity.ChatHistory;

import java.util.List;

/**
 * 对话记录服务接口
 *
 * @author 海克斯
 */
public interface ChatHistoryService {

    /**
     * 保存对话记录
     *
     * @param history 对话记录
     * @return 是否成功
     */
    boolean save(ChatHistory history);

    /**
     * 批量保存对话记录
     *
     * @param histories 对话记录列表
     * @return 是否成功
     */
    boolean saveBatch(List<ChatHistory> histories);

    /**
     * 获取会话历史
     *
     * @param sessionId 会话ID
     * @param limit     限制条数
     * @return 历史记录列表
     */
    List<ChatHistory> getHistory(String sessionId, int limit);

    /**
     * 获取用户的历史会话ID
     *
     * @param userId 用户ID
     * @return 会话ID列表
     */
    List<String> getUserSessions(Long userId);

    /**
     * 获取用户的所有历史记录（按会话分组）
     *
     * @param userId 用户ID
     * @return 历史记录列表
     */
    List<ChatHistory> getUserHistory(Long userId);

    /**
     * 构建上下文提示词
     *
     * @param sessionId 会话ID
     * @param limit     限制条数
     * @return 格式化的上下文
     */
    String buildContextPrompt(String sessionId, int limit);
}
