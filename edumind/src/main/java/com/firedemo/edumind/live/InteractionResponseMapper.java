package com.firedemo.edumind.live;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.*;
import java.util.List;

@Mapper
public interface InteractionResponseMapper extends BaseMapper<InteractionResponse> {
    @Insert("INSERT INTO interaction_response (interaction_id, session_id, student_id, student_name, answer, is_correct, score, responded_at) " +
            "VALUES (#{interactionId}, #{sessionId}, #{studentId}, #{studentName}, #{answer}, #{isCorrect}, #{score}, NOW()) " +
            "ON CONFLICT (interaction_id, student_id) DO UPDATE SET answer = #{answer}, is_correct = #{isCorrect}, score = #{score}, responded_at = NOW()")
    int upsert(InteractionResponse r);
    @Select("SELECT * FROM interaction_response WHERE interaction_id = #{interactionId}")
    List<InteractionResponse> findByInteractionId(@Param("interactionId") Long interactionId);
    @Select("SELECT * FROM interaction_response WHERE session_id = #{sessionId}")
    List<InteractionResponse> findBySessionId(@Param("sessionId") Long sessionId);
    @Select("<script>SELECT * FROM interaction_response WHERE session_id IN " +
            "<foreach collection='sessionIds' item='id' open='(' separator=',' close=')'>#{id}</foreach></script>")
    List<InteractionResponse> findBySessionIds(@Param("sessionIds") List<Long> sessionIds);
    @Select("SELECT COUNT(*) FROM interaction_response WHERE interaction_id = #{interactionId}")
    int countByInteractionId(@Param("interactionId") Long interactionId);
}
