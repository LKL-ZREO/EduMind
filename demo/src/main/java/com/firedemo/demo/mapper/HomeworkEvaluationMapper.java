package com.firedemo.demo.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.firedemo.demo.Entity.HomeworkEvaluation;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 作业评价Mapper
 */
@Mapper
public interface HomeworkEvaluationMapper extends BaseMapper<HomeworkEvaluation> {

    /**
     * 查询班级所有作业评价
     */
    @Select("SELECT * FROM homework_evaluation WHERE class_id = #{classId} ORDER BY created_at DESC")
    List<HomeworkEvaluation> selectByClassId(@Param("classId") Long classId);

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
     * 查询学生的作业评价列表
     */
    @Select("SELECT * FROM homework_evaluation WHERE user_id = #{userId} ORDER BY created_at DESC")
    List<HomeworkEvaluation> selectByUserId(@Param("userId") Long userId);
}
