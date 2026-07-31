package com.firedemo.edumind.live;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.*;

@Mapper
public interface ClassroomSessionMapper extends BaseMapper<ClassroomSession> {
    @Select("SELECT * FROM classroom_session WHERE session_code = #{code} AND status = 'ACTIVE' LIMIT 1")
    ClassroomSession findByCode(@Param("code") String code);
    @Select("SELECT * FROM classroom_session WHERE class_id = #{classId} AND status = 'ACTIVE' ORDER BY started_at DESC LIMIT 1")
    ClassroomSession findActiveByClassId(@Param("classId") Long classId);
    @Update("UPDATE classroom_session SET status = 'ENDED', ended_at = NOW(), teacher_offline_at = NULL " +
            "WHERE id = #{id} AND status = 'ACTIVE'")
    int endSession(@Param("id") Long id);
    @Update("UPDATE classroom_session SET teacher_offline_at = NULL " +
            "WHERE id = #{id} AND status = 'ACTIVE'")
    int markTeacherOnline(@Param("id") Long id);
    @Update("UPDATE classroom_session SET teacher_offline_at = COALESCE(teacher_offline_at, NOW()) " +
            "WHERE id = #{id} AND status = 'ACTIVE'")
    int markTeacherOffline(@Param("id") Long id);
    @Select("SELECT * FROM classroom_session WHERE status = 'ACTIVE' " +
            "AND teacher_offline_at IS NOT NULL AND teacher_offline_at <= #{cutoff}")
    java.util.List<ClassroomSession> findOfflineBefore(
            @Param("cutoff") java.time.LocalDateTime cutoff);
    @Select("SELECT * FROM classroom_session WHERE class_id = #{classId} ORDER BY started_at DESC")
    java.util.List<ClassroomSession> findByClassId(@Param("classId") Long classId);
    @Select("SELECT * FROM classroom_session WHERE id = #{id} FOR UPDATE")
    ClassroomSession selectByIdForUpdate(@Param("id") Long id);
}
