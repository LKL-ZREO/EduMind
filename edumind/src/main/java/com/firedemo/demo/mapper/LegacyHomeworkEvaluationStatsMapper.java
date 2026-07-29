package com.firedemo.demo.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * Read-only statistics over the legacy homework_evaluation table.
 * New grading results are persisted in submission; this mapper exists only so
 * historical scores remain visible until the legacy table is retired.
 */
@Mapper
public interface LegacyHomeworkEvaluationStatsMapper {

    @Select("SELECT total_score FROM homework_evaluation "
            + "WHERE class_id = #{classId} AND total_score IS NOT NULL")
    List<Integer> selectScoresByClassId(@Param("classId") Long classId);

    @Select("SELECT COUNT(DISTINCT session_id) FROM homework_evaluation WHERE class_id = #{classId}")
    Integer countByClassId(@Param("classId") Long classId);

    @Select("SELECT COUNT(DISTINCT session_id) FROM homework_evaluation "
            + "WHERE class_id = #{classId} AND created_at >= #{startTime}")
    Integer countNewByClassId(@Param("classId") Long classId,
                              @Param("startTime") LocalDateTime startTime);

    @Select("SELECT user_id, COUNT(*) AS homework_count, AVG(total_score) AS avg_score "
            + "FROM homework_evaluation "
            + "WHERE class_id = #{classId} AND total_score IS NOT NULL "
            + "GROUP BY user_id")
    List<Map<String, Object>> selectStudentStatsByClassId(@Param("classId") Long classId);
}
