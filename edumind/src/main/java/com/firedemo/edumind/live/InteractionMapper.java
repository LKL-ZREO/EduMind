package com.firedemo.edumind.live;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.*;
import java.util.List;

@Mapper
public interface InteractionMapper extends BaseMapper<Interaction> {
    @Select("SELECT * FROM interaction WHERE session_id = #{sessionId} ORDER BY sort_order")
    List<Interaction> findBySessionId(@Param("sessionId") Long sessionId);
    @Select("<script>SELECT * FROM interaction WHERE session_id IN " +
            "<foreach collection='sessionIds' item='id' open='(' separator=',' close=')'>#{id}</foreach> ORDER BY sort_order</script>")
    List<Interaction> findBySessionIds(@Param("sessionIds") List<Long> sessionIds);
    @Select("SELECT * FROM interaction WHERE session_id = #{sessionId} AND status = 'ACTIVE' ORDER BY sort_order LIMIT 1")
    Interaction findActiveBySessionId(@Param("sessionId") Long sessionId);
    @Update("UPDATE interaction SET status = 'CLOSED', closed_at = NOW() " +
            "WHERE id = #{id} AND session_id = #{sessionId} AND status = 'ACTIVE'")
    int closeInteraction(@Param("id") Long id, @Param("sessionId") Long sessionId);

    @Update("UPDATE interaction SET deadline_at = #{deadlineAt} " +
            "WHERE id = #{id} AND session_id = #{sessionId} AND status = 'ACTIVE'")
    int updateDeadline(@Param("id") Long id,
                       @Param("sessionId") Long sessionId,
                       @Param("deadlineAt") java.time.LocalDateTime deadlineAt);

    @Select("SELECT * FROM interaction WHERE status = 'ACTIVE' AND deadline_at IS NOT NULL")
    List<Interaction> findActiveWithDeadline();

    @Insert("INSERT INTO interaction (question_id, session_id, class_id, source_doc_id, type, title, description, options, correct_key, explanation, time_limit, status, sort_order, ai_generated, knowledge_point, difficulty, created_at, activated_at, deadline_at) " +
            "VALUES (#{questionId}, #{sessionId}, #{classId}, #{sourceDocId}, #{type}, #{title}, #{description}, CAST(#{options} AS jsonb), #{correctKey}, #{explanation}, #{timeLimit}, #{status}, #{sortOrder}, #{aiGenerated}, #{knowledgePoint}, #{difficulty}, NOW(), #{activatedAt}, #{deadlineAt})")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insertWithJsonb(Interaction interaction);
}
