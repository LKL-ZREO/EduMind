package com.firedemo.demo.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.firedemo.demo.Entity.Interaction;
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
    @Update("UPDATE interaction SET status = 'CLOSED', closed_at = NOW() WHERE id = #{id}")
    int closeInteraction(@Param("id") Long id);

    @Select("SELECT * FROM interaction WHERE class_id = #{classId} AND status = 'DRAFT' ORDER BY created_at DESC")
    List<Interaction> findDraftsByClassId(@Param("classId") Long classId);

    @Update("UPDATE interaction SET status = 'ACTIVE', session_id = #{sessionId} WHERE id = #{id}")
    int activateDraft(@Param("id") Long id, @Param("sessionId") Long sessionId);

    @Delete("DELETE FROM interaction WHERE id = #{id} AND status = 'DRAFT'")
    int deleteDraft(@Param("id") Long id);

    @Select("SELECT * FROM interaction WHERE source_doc_id = #{docId} AND status = 'DRAFT' ORDER BY created_at DESC")
    List<Interaction> findDraftsByDocId(@Param("docId") String docId);

    @Insert("INSERT INTO interaction (session_id, class_id, source_doc_id, type, title, description, options, correct_key, time_limit, status, sort_order, ai_generated, knowledge_point, created_at) " +
            "VALUES (#{sessionId}, #{classId}, #{sourceDocId}, #{type}, #{title}, #{description}, CAST(#{options} AS jsonb), #{correctKey}, #{timeLimit}, #{status}, #{sortOrder}, #{aiGenerated}, #{knowledgePoint}, NOW())")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insertWithJsonb(Interaction interaction);
}
