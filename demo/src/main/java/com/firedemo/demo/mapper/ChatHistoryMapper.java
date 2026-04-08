package com.firedemo.demo.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.firedemo.demo.entity.ChatHistory;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 对话记录Mapper
 *
 * @author 海克斯
 */
@Mapper
public interface ChatHistoryMapper extends BaseMapper<ChatHistory> {

    /**
     * 查询会话历史记录
     *
     * @param sessionId 会话ID
     * @param limit     限制条数
     * @return 历史记录列表
     */
    @Select("SELECT * FROM chat_history WHERE session_id = #{sessionId} ORDER BY created_at ASC LIMIT #{limit}")
    List<ChatHistory> selectBySessionId(@Param("sessionId") String sessionId, @Param("limit") int limit);

    /**
     * 查询用户的所有会话ID
     *
     * @param userId 用户ID
     * @return 会话ID列表
     */
    @Select("SELECT DISTINCT session_id FROM chat_history WHERE user_id = #{userId} ORDER BY MAX(created_at) DESC")
    List<String> selectSessionIdsByUserId(@Param("userId") Long userId);

    /**
     * 查询用户的所有历史记录
     *
     * @param userId 用户ID
     * @return 历史记录列表
     */
    @Select("SELECT * FROM chat_history WHERE user_id = #{userId} ORDER BY session_id, created_at ASC")
    List<ChatHistory> selectByUserId(@Param("userId") Long userId);
}
