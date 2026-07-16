package com.firedemo.demo.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.firedemo.demo.Entity.TeachingCalendar;
import org.apache.ibatis.annotations.*;

import java.util.List;

@Mapper
public interface TeachingCalendarMapper extends BaseMapper<TeachingCalendar> {

    @Select("SELECT * FROM teaching_calendar WHERE class_id = #{classId} ORDER BY week_number, planned_date")
    List<TeachingCalendar> findByClassId(@Param("classId") Long classId);

    @Insert("INSERT INTO teaching_calendar (class_id, teacher_id, week_number, planned_date, topic, knowledge_points, status) " +
            "VALUES (#{classId}, #{teacherId}, #{weekNumber}, #{plannedDate}, #{topic}, #{knowledgePoints}, 'PLANNED')")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insertPlan(TeachingCalendar plan);

    @Update("UPDATE teaching_calendar SET session_id = #{sessionId}, status = 'COMPLETED', updated_at = NOW() WHERE id = #{id}")
    int markCompleted(@Param("id") Long id, @Param("sessionId") Long sessionId);

    @Delete("DELETE FROM teaching_calendar WHERE id = #{id}")
    int deletePlan(@Param("id") Long id);
}
