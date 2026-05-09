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

    @Select("SELECT * FROM submission WHERE class_id = #{classId} ORDER BY submitted_at DESC")
    List<Submission> selectByClassId(@Param("classId") Long classId);

    @Select("SELECT * FROM submission WHERE student_name = #{studentName} AND class_id = #{classId} ORDER BY submitted_at DESC")
    List<Submission> selectByStudentAndClass(@Param("studentName") String studentName, @Param("classId") Long classId);

    @Select("SELECT COUNT(*) FROM submission WHERE class_id = #{classId}")
    Integer countByClassId(@Param("classId") Long classId);

    @Select("SELECT COUNT(*) FROM submission WHERE class_id = #{classId} AND submitted_at >= #{since}")
    Integer countNewByClassId(@Param("classId") Long classId, @Param("since") LocalDateTime since);

    @Select("SELECT total_score FROM submission WHERE class_id = #{classId} AND total_score IS NOT NULL")
    List<Integer> selectScoresByClassId(@Param("classId") Long classId);

    @Select("SELECT hk.knowledge_point, AVG(hk.mastery) as avg_mastery, COUNT(*) as count " +
            "FROM homework_knowledge hk " +
            "WHERE hk.submission_id IN (SELECT id FROM submission WHERE class_id = #{classId}) " +
            "GROUP BY hk.knowledge_point ORDER BY avg_mastery ASC")
    List<Map<String, Object>> selectKnowledgeStatsByClassId(@Param("classId") Long classId);

    @Select("SELECT DISTINCT hk.knowledge_point " +
            "FROM homework_knowledge hk " +
            "WHERE hk.submission_id IN (SELECT id FROM submission WHERE class_id = #{classId}) AND hk.mastery < 70")
    List<String> selectWeakKnowledgePoints(@Param("classId") Long classId);

    @Select("SELECT student_name, COUNT(*) as homework_count, AVG(total_score) as avg_score " +
            "FROM submission WHERE class_id = #{classId} " +
            "GROUP BY student_name ORDER BY avg_score DESC NULLS LAST")
    List<Map<String, Object>> selectStudentOverviewByClassId(@Param("classId") Long classId);

    @Select("SELECT COUNT(DISTINCT student_name) FROM submission WHERE class_id = #{classId}")
    Integer countDistinctStudentsByClassId(@Param("classId") Long classId);

    /**
     * 查询学生提交记录，按作业序号排序（用于成长曲线）
     */
    @Select("SELECT * FROM submission WHERE student_name = #{studentName} AND class_id = #{classId} " +
            "AND assignment_no IS NOT NULL ORDER BY assignment_no ASC")
    List<Submission> selectByStudentAndClassOrderByNo(@Param("studentName") String studentName, @Param("classId") Long classId);
}
