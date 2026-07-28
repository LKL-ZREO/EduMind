package com.firedemo.demo.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.firedemo.demo.Entity.LiveAttendanceLog;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;
import java.util.Map;

@Mapper
public interface LiveAttendanceLogMapper extends BaseMapper<LiveAttendanceLog> {

    @Select("SELECT student_id, student_name, MIN(event_time) AS first_join, MAX(event_time) AS last_event " +
            "FROM live_attendance_log WHERE session_id = #{sessionId} AND event = 'JOIN' " +
            "GROUP BY student_id, student_name ORDER BY first_join")
    List<Map<String, Object>> getAttendanceSummary(@Param("sessionId") Long sessionId);
}
