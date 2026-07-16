package com.firedemo.demo.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.firedemo.demo.Entity.LiveConfusionEvent;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;
import java.util.Map;

@Mapper
public interface LiveConfusionEventMapper extends BaseMapper<LiveConfusionEvent> {

    /** 按知识点聚合该课堂的不懂次数 */
    @Select("""
        SELECT knowledge_point AS name, COUNT(*) AS count
        FROM live_confusion_event
        WHERE session_id = #{sessionId}
        GROUP BY knowledge_point
        ORDER BY count DESC
    """)
    List<Map<String, Object>> countByKnowledgePoint(@Param("sessionId") Long sessionId);

    /** 该课堂的不懂事件列表 */
    @Select("SELECT * FROM live_confusion_event WHERE session_id = #{sessionId} ORDER BY created_at DESC")
    List<LiveConfusionEvent> findBySessionId(@Param("sessionId") Long sessionId);

    /** 按班级聚合所有课堂的不懂次数（跨 session 汇总） */
    @Select("""
        SELECT e.knowledge_point AS name, COUNT(*) AS count
        FROM live_confusion_event e
        JOIN classroom_session s ON e.session_id = s.id
        WHERE s.class_id = #{classId}
        GROUP BY e.knowledge_point
        ORDER BY count DESC
        LIMIT #{limit}
    """)
    List<Map<String, Object>> countByClassId(@Param("classId") Long classId, @Param("limit") int limit);

    /** 班级所有课堂的不懂事件明细 */
    @Select("""
        SELECT e.* FROM live_confusion_event e
        JOIN classroom_session s ON e.session_id = s.id
        WHERE s.class_id = #{classId}
        ORDER BY e.created_at DESC
        LIMIT #{limit}
    """)
    List<LiveConfusionEvent> findByClassId(@Param("classId") Long classId, @Param("limit") int limit);
}
