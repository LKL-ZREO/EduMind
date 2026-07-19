package com.firedemo.demo.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.firedemo.demo.Entity.ClassroomSession;
import org.apache.ibatis.annotations.*;

@Mapper
public interface ClassroomSessionMapper extends BaseMapper<ClassroomSession> {
    @Select("SELECT * FROM classroom_session WHERE session_code = #{code} AND status = 'ACTIVE' LIMIT 1")
    ClassroomSession findByCode(@Param("code") String code);
    @Select("SELECT * FROM classroom_session WHERE class_id = #{classId} AND status = 'ACTIVE' ORDER BY started_at DESC LIMIT 1")
    ClassroomSession findActiveByClassId(@Param("classId") Long classId);
    @Update("UPDATE classroom_session SET status = 'ENDED', ended_at = NOW() WHERE id = #{id}")
    int endSession(@Param("id") Long id);
    @Select("SELECT * FROM classroom_session WHERE class_id = #{classId} ORDER BY started_at DESC")
    java.util.List<ClassroomSession> findByClassId(@Param("classId") Long classId);
}
