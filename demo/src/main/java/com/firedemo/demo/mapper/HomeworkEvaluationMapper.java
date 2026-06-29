package com.firedemo.demo.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.firedemo.demo.Entity.HomeworkEvaluation;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 作业评价Mapper
 */
@Mapper
public interface HomeworkEvaluationMapper extends BaseMapper<HomeworkEvaluation> {

    /**
     * 查询班级所有作业评价（仅分数，不含 raw_response）
     */
    @Select("SELECT total_score FROM homework_evaluation WHERE class_id = #{classId} AND total_score IS NOT NULL")
    List<Integer> selectScoresByClassId(@Param("classId") Long classId);

    /**
     * 查询班级所有作业评价
     */

    /**
     * 查询班级作业数量
     */
    @Select("SELECT COUNT(DISTINCT session_id) FROM homework_evaluation WHERE class_id = #{classId}")
    Integer countByClassId(@Param("classId") Long classId);

    /**
     * 查询班级本周新增作业数量
     */
    @Select("SELECT COUNT(DISTINCT session_id) FROM homework_evaluation WHERE class_id = #{classId} AND created_at >= #{startTime}")
    Integer countNewByClassId(@Param("classId") Long classId, @Param("startTime") LocalDateTime startTime);

    /**
     * 批量查询学生平均分和错误数（避免N+1）
     */
    @Select("SELECT user_id, COUNT(*) as homework_count, AVG(total_score) as avg_score " +
            "FROM homework_evaluation WHERE class_id = #{classId} AND total_score IS NOT NULL " +
            "GROUP BY user_id")
    List<Map<String, Object>> selectStudentStatsByClassId(@Param("classId") Long classId);
}
