package com.firedemo.demo.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.firedemo.demo.Entity.ClassStudent;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Insert;

import java.util.List;
import java.util.Map;

/**
 * 班级学生Mapper
 */
@Mapper
public interface ClassStudentMapper extends BaseMapper<ClassStudent> {

    /**
     * 插入或忽略（已存在则跳过）
     */
    @Insert("INSERT INTO class_student (class_id, student_id, student_name, source, created_at) " +
            "VALUES (#{classId}, #{studentId}, #{studentName}, #{source}, NOW()) " +
            "ON CONFLICT (class_id, student_id) DO NOTHING")
    int insertIgnore(@Param("classId") Long classId,
                     @Param("studentId") String studentId,
                     @Param("studentName") String studentName,
                     @Param("source") String source);

    /**
     * 查询班级的所有学生
     */
    @Select("SELECT * FROM class_student WHERE class_id = #{classId}")
    List<ClassStudent> selectByClassId(@Param("classId") Long classId);

    /**
     * 根据学号查学生（获取其班级ID）
     */
    @Select("SELECT * FROM class_student WHERE student_id = #{studentId} LIMIT 1")
    ClassStudent selectByStudentId(@Param("studentId") String studentId);

    /**
     * 查询某作业未提交的学生（带QQ号）
     */
    @Select("SELECT cs.student_id, cs.student_name, b.qq_number " +
            "FROM class_student cs " +
            "LEFT JOIN submission s ON cs.student_id = s.student_id AND s.task_id = #{taskId} " +
            "LEFT JOIN student_qq_binding b ON cs.student_id = b.student_id " +
            "WHERE cs.class_id = #{classId} AND s.id IS NULL")
    List<Map<String, Object>> selectUnsubmittedByTaskId(@Param("classId") Long classId,
                                                        @Param("taskId") Long taskId);

    /**
     * 查询某作业已提交的学生数
     */
    @Select("SELECT COUNT(DISTINCT s.student_id) FROM submission s " +
            "WHERE s.task_id = #{taskId} AND s.student_id IN (" +
            "  SELECT student_id FROM class_student WHERE class_id = #{classId}" +
            ")")
    Integer countSubmittedByTaskId(@Param("classId") Long classId,
                                   @Param("taskId") Long taskId);

    /**
     * 查询班级学生总数
     */
    @Select("SELECT COUNT(*) FROM class_student WHERE class_id = #{classId}")
    Integer countByClassId(@Param("classId") Long classId);

    /**
     * 批量查询各班级学生数（避免 N+1）
     */
    @Select("<script>" +
            "SELECT class_id, COUNT(*) as cnt FROM class_student " +
            "WHERE class_id IN " +
            "<foreach collection='classIds' item='id' open='(' separator=',' close=')'>" +
            "#{id}" +
            "</foreach>" +
            " GROUP BY class_id" +
            "</script>")
    List<Map<String, Object>> countByClassIds(@Param("classIds") List<Long> classIds);
}
