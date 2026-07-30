package com.firedemo.demo.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.firedemo.demo.Entity.ChatSession;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

@Mapper
public interface ChatSessionMapper extends BaseMapper<ChatSession> {

    @Select("SELECT * FROM chat_session WHERE user_id = #{userId} " +
            "ORDER BY pinned DESC, updated_at DESC, created_at DESC")
    List<ChatSession> selectByUserId(@Param("userId") Long userId);

    @Select("SELECT * FROM chat_session WHERE session_id = #{sessionId} AND user_id = #{userId}")
    ChatSession selectOwned(@Param("userId") Long userId,
                            @Param("sessionId") String sessionId);

    @Insert("""
            INSERT INTO chat_session
                (session_id, user_id, title, class_id, course_id, selected_kb_ids,
                 mode, pinned, created_at, updated_at)
            VALUES
                (#{sessionId}, #{userId}, #{title}, #{classId}, #{courseId}, #{selectedKbIds},
                 #{mode}, FALSE, NOW(), NOW())
            ON CONFLICT (session_id) DO NOTHING
            """)
    int insertIfAbsent(ChatSession session);

    @Update("""
            UPDATE chat_session
               SET title = #{title},
                   class_id = #{classId},
                   course_id = #{courseId},
                   selected_kb_ids = #{selectedKbIds},
                   mode = #{mode},
                   pinned = #{pinned},
                   updated_at = NOW()
             WHERE session_id = #{sessionId} AND user_id = #{userId}
            """)
    int updateOwned(ChatSession session);

    @Update("""
            UPDATE chat_session
               SET title = CASE WHEN title = '新对话' THEN #{title} ELSE title END,
                   updated_at = NOW()
             WHERE session_id = #{sessionId} AND user_id = #{userId}
            """)
    int touchAndAutoTitle(@Param("userId") Long userId,
                          @Param("sessionId") String sessionId,
                          @Param("title") String title);

    @Delete("DELETE FROM chat_session WHERE session_id = #{sessionId} AND user_id = #{userId}")
    int deleteOwned(@Param("userId") Long userId,
                    @Param("sessionId") String sessionId);

    @Delete("DELETE FROM chat_session WHERE user_id = #{userId}")
    int deleteByUserId(@Param("userId") Long userId);
}
