package com.firedemo.demo.mapper;

import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface AgentChatMemoryMapper {

    @Select("SELECT messages_json::text FROM agent_chat_memory WHERE memory_key = #{memoryKey}")
    String selectMessagesJson(@Param("memoryKey") String memoryKey);

    @Insert("""
            INSERT INTO agent_chat_memory
                (memory_key, user_id, session_id, messages_json, created_at, updated_at)
            VALUES
                (#{memoryKey}, #{userId}, #{sessionId}, CAST(#{messagesJson} AS jsonb), NOW(), NOW())
            ON CONFLICT (memory_key) DO UPDATE SET
                user_id = EXCLUDED.user_id,
                session_id = EXCLUDED.session_id,
                messages_json = EXCLUDED.messages_json,
                updated_at = NOW()
            """)
    int upsert(@Param("memoryKey") String memoryKey,
               @Param("userId") Long userId,
               @Param("sessionId") String sessionId,
               @Param("messagesJson") String messagesJson);

    @Delete("DELETE FROM agent_chat_memory WHERE memory_key = #{memoryKey}")
    int deleteByMemoryKey(@Param("memoryKey") String memoryKey);

    @Delete("DELETE FROM agent_chat_memory WHERE user_id = #{userId}")
    int deleteByUserId(@Param("userId") Long userId);

    @Select("SELECT session_id FROM agent_chat_memory WHERE user_id = #{userId}")
    List<String> selectSessionIdsByUserId(@Param("userId") Long userId);
}
