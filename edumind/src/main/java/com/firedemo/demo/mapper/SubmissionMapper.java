package com.firedemo.demo.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.firedemo.demo.Entity.Submission;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 作业提交记录Mapper
 */
@Mapper
public interface SubmissionMapper extends BaseMapper<Submission> {

    /**
     * 查询班级所有提交（每个学生每个作业只取最新）
     */
    @Select("SELECT s.* FROM submission s " +
            "INNER JOIN (" +
            "  SELECT student_id, task_id, MAX(submitted_at) as max_time " +
            "  FROM submission WHERE class_id = #{classId} AND student_id IS NOT NULL " +
            "  GROUP BY student_id, task_id" +
            ") latest ON s.student_id = latest.student_id AND s.task_id = latest.task_id AND s.submitted_at = latest.max_time " +
            "ORDER BY s.submitted_at DESC")
    List<Submission> selectByClassId(@Param("classId") Long classId);

    /**
     * 查询学生提交记录（每个作业只取最新）
     */
    @Select("SELECT s.* FROM submission s " +
            "INNER JOIN (" +
            "  SELECT task_id, MAX(submitted_at) as max_time " +
            "  FROM submission WHERE student_name = #{studentName} AND class_id = #{classId} " +
            "  GROUP BY task_id" +
            ") latest ON s.task_id = latest.task_id AND s.submitted_at = latest.max_time " +
            "ORDER BY s.submitted_at DESC")
    List<Submission> selectByStudentAndClass(@Param("studentName") String studentName, @Param("classId") Long classId);

    /**
     * 统计班级提交数（每个学生每个作业只算一次）
     */
    @Select("SELECT COUNT(*) FROM (" +
            "  SELECT DISTINCT student_id, task_id FROM submission " +
            "  WHERE class_id = #{classId} AND student_id IS NOT NULL AND status = 'COMPLETED'" +
            ") t")
    Integer countByClassId(@Param("classId") Long classId);

    /**
     * 统计班级新增提交数（每个学生每个作业只算一次）
     */
    @Select("SELECT COUNT(*) FROM (" +
            "  SELECT DISTINCT student_id, task_id FROM submission " +
            "  WHERE class_id = #{classId} AND submitted_at >= #{since} AND student_id IS NOT NULL AND status = 'COMPLETED'" +
            ") t")
    Integer countNewByClassId(@Param("classId") Long classId, @Param("since") LocalDateTime since);

    /**
     * 查询班级所有分数（每个学生每个作业只取最新）
     */
    @Select("SELECT s.total_score FROM submission s " +
            "INNER JOIN (" +
            "  SELECT student_id, task_id, MAX(submitted_at) as max_time " +
            "  FROM submission WHERE class_id = #{classId} AND student_id IS NOT NULL " +
            "  GROUP BY student_id, task_id" +
            ") latest ON s.student_id = latest.student_id AND s.task_id = latest.task_id AND s.submitted_at = latest.max_time " +
            "WHERE s.total_score IS NOT NULL")
    List<Integer> selectScoresByClassId(@Param("classId") Long classId);



    /**
     * 查询学生概览（每个作业只取最新，按学号分组）
     */
    @Select("SELECT s.student_id as student_id, s.student_name as student_name, " +
            "COUNT(*) as homework_count, AVG(s.total_score) as avg_score " +
            "FROM submission s " +
            "INNER JOIN (" +
            "  SELECT student_id, task_id, MAX(submitted_at) as max_time " +
            "  FROM submission WHERE class_id = #{classId} AND student_id IS NOT NULL AND status = 'COMPLETED'" +
            "  GROUP BY student_id, task_id" +
            ") latest ON s.student_id = latest.student_id AND s.task_id = latest.task_id AND s.submitted_at = latest.max_time " +
            "WHERE s.status = 'COMPLETED' " +
            "GROUP BY s.student_id, s.student_name ORDER BY avg_score DESC NULLS LAST")
    List<Map<String, Object>> selectStudentOverviewByClassId(@Param("classId") Long classId);

    /**
     * 统计班级学生数（按学号去重）
     */
    @Select("SELECT COUNT(DISTINCT student_id) FROM submission WHERE class_id = #{classId} AND student_id IS NOT NULL AND status = 'COMPLETED'")
    Integer countDistinctStudentsByClassId(@Param("classId") Long classId);

    /**
     * 查询学生提交记录，按作业序号排序（用于成长曲线，每个作业只取最新）
     */
    @Select("SELECT s.id, s.student_name, s.student_id, s.class_name, s.class_id, " +
            "s.assignment_name, s.file_name, s.file_path, s.file_size, " +
            "s.total_score, s.content_score, s.overall_comment, " +
            "s.strengths, s.weaknesses, s.suggestions, s.status, s.error_message, " +
            "s.submitted_at, s.task_id, s.submit_count, s.remaining_attempts, " +
            "s.is_late, s.penalty_applied, s.final_score, s.assignment_no " +
            "FROM submission s " +
            "INNER JOIN (" +
            "  SELECT task_id, MAX(submitted_at) as max_time " +
            "  FROM submission WHERE student_name = #{studentName} AND class_id = #{classId} " +
            "  GROUP BY task_id" +
            ") latest ON s.task_id = latest.task_id AND s.submitted_at = latest.max_time " +
            "WHERE s.assignment_no IS NOT NULL ORDER BY s.assignment_no ASC")
    List<Submission> selectByStudentAndClassOrderByNo(@Param("studentName") String studentName, @Param("classId") Long classId);

    /**
     * 根据学号查询学生提交记录，按作业序号排序
     */
    @Select("SELECT s.id, s.student_name, s.student_id, s.class_name, s.class_id, " +
            "s.assignment_name, s.file_name, s.file_path, s.file_size, " +
            "s.total_score, s.content_score, s.overall_comment, " +
            "s.strengths, s.weaknesses, s.suggestions, s.status, s.error_message, " +
            "s.submitted_at, s.task_id, s.submit_count, s.remaining_attempts, " +
            "s.is_late, s.penalty_applied, s.final_score, s.assignment_no " +
            "FROM submission s " +
            "INNER JOIN (" +
            "  SELECT task_id, MAX(submitted_at) as max_time " +
            "  FROM submission WHERE student_id = #{studentId} AND class_id = #{classId} " +
            "  GROUP BY task_id" +
            ") latest ON s.task_id = latest.task_id AND s.submitted_at = latest.max_time " +
            "WHERE s.assignment_no IS NOT NULL ORDER BY s.assignment_no ASC")
    List<Submission> selectByStudentIdAndClassOrderByNo(@Param("studentId") String studentId, @Param("classId") Long classId);

    /**
     * 根据学号和作业任务ID查询提交次数
     */
    @Select("SELECT COUNT(*) FROM submission WHERE student_id = #{studentId} AND task_id = #{taskId}")
    Integer countByStudentIdAndTaskId(@Param("studentId") String studentId, @Param("taskId") Long taskId);

    /**
     * 批量查询班级下所有作业的统计（每个学生只取最新提交）
     */
    @Select("SELECT task_id, COUNT(DISTINCT student_id) as submitted_count, " +
            "ROUND(AVG(total_score)) as avg_score FROM submission " +
            "WHERE class_id = #{classId} AND total_score IS NOT NULL " +
            "GROUP BY task_id")
    List<Map<String, Object>> selectTaskStatsByClassId(@Param("classId") Long classId);
}
