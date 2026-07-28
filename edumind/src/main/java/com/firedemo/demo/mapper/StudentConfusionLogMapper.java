package com.firedemo.demo.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.firedemo.demo.Entity.StudentConfusionLog;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;
import java.util.Map;

@Mapper
public interface StudentConfusionLogMapper extends BaseMapper<StudentConfusionLog> {

    @Select("SELECT * FROM student_confusion_log WHERE class_id = #{classId} ORDER BY created_at DESC LIMIT #{limit}")
    List<StudentConfusionLog> findByClassId(@Param("classId") Long classId, @Param("limit") int limit);

    /** 按知识点聚合计数（最近30天） */
    @Select("""
        SELECT knowledge_point AS name, COUNT(*) AS count
        FROM student_confusion_log
        WHERE class_id = #{classId}
          AND created_at > NOW() - INTERVAL '30 days'
        GROUP BY knowledge_point
        ORDER BY count DESC
        LIMIT #{limit}
    """)
    List<Map<String, Object>> countByKnowledgePoint(@Param("classId") Long classId, @Param("limit") int limit);
}
