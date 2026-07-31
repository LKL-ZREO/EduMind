package com.firedemo.edumind.homework;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;
import java.util.Map;

/**
 * 作业错误分类明细 Mapper
 */
@Mapper
public interface SubmissionErrorMapper extends BaseMapper<SubmissionError> {

    /**
     * 按知识点统计错误数（热力图用）
     */
    @Select("SELECT knowledge_point, COUNT(*) as error_count, " +
            "SUM(CASE WHEN severity = 'critical' THEN 1 ELSE 0 END) as critical_count, " +
            "SUM(CASE WHEN severity = 'major' THEN 1 ELSE 0 END) as major_count " +
            "FROM submission_errors " +
            "WHERE class_id = #{classId} " +
            "GROUP BY knowledge_point " +
            "ORDER BY error_count DESC")
    List<Map<String, Object>> selectErrorStatsByClassId(@Param("classId") Long classId);

    /**
     * 查询班级的薄弱知识点（错误较多，掌握度 < 70% 估算）
     */
    @Select("SELECT knowledge_point, COUNT(*) as error_count " +
            "FROM submission_errors " +
            "WHERE class_id = #{classId} " +
            "GROUP BY knowledge_point " +
            "HAVING COUNT(*) > 5 " +
            "ORDER BY error_count DESC")
    List<Map<String, Object>> selectWeakKnowledgePoints(@Param("classId") Long classId);

    /**
     * 查询某个知识点下的所有错误明细（knowledgePoint=null 时查全部）
     */
    @Select("<script>" +
            "SELECT * FROM submission_errors WHERE class_id = #{classId}" +
            "<if test='knowledgePoint != null'> AND knowledge_point = #{knowledgePoint}</if>" +
            " ORDER BY created_at DESC LIMIT #{limit}" +
            "</script>")
    List<SubmissionError> selectByClassIdAndKnowledgePoint(
            @Param("classId") Long classId,
            @Param("knowledgePoint") String knowledgePoint,
            @Param("limit") int limit);

    /**
     * 批量插入错误记录（替代逐条 INSERT）
     */
    @org.apache.ibatis.annotations.Insert("<script>" +
            "INSERT INTO submission_errors (submission_id, class_id, error_text, error_type, severity, knowledge_point, created_at, updated_at) VALUES " +
            "<foreach collection='list' item='e' separator=','>" +
            "(#{e.submissionId}, #{e.classId}, #{e.errorText}, #{e.errorType}, #{e.severity}, #{e.knowledgePoint}, #{e.createdAt}, #{e.updatedAt})" +
            "</foreach>" +
            "</script>")
    int insertBatch(@Param("list") List<SubmissionError> errors);

    /**
     * 查询某个班级所有未归类的错误（用于重分类）
     */
    @Select("SELECT * FROM submission_errors " +
            "WHERE class_id = #{classId} AND knowledge_point = '其他' " +
            "ORDER BY created_at ASC")
    List<SubmissionError> selectUnclassifiedByClassId(@Param("classId") Long classId);

    /**
     * 聚合知识点错误诊断，并关联提交记录补充受影响学生数、作业数和最近出现时间。
     */
    @Select("<script>" +
            "SELECT se.error_text AS question, se.knowledge_point, COUNT(*) AS error_count, " +
            "MAX(CASE " +
            "  WHEN se.severity IN ('critical', 'high') THEN 3 " +
            "  WHEN se.severity IN ('major', 'medium') THEN 2 ELSE 1 END) AS severity_rank, " +
            "COUNT(DISTINCT COALESCE(NULLIF(s.student_id, ''), s.student_name)) AS affected_student_count, " +
            "COUNT(DISTINCT COALESCE(s.task_id, -s.id)) AS assignment_count, " +
            "MAX(se.created_at) AS latest_seen_at " +
            "FROM submission_errors se " +
            "JOIN submission s ON s.id = se.submission_id " +
            "WHERE se.class_id = #{classId} " +
            "<if test='knowledgePoint != null and knowledgePoint != \"\" and knowledgePoint != \"全部\"'>" +
            " AND se.knowledge_point = #{knowledgePoint}" +
            "</if> " +
            "GROUP BY se.error_text, se.knowledge_point " +
            "ORDER BY error_count DESC, latest_seen_at DESC LIMIT #{limit}" +
            "</script>")
    List<Map<String, Object>> selectFrequentErrorStats(
            @Param("classId") Long classId,
            @Param("knowledgePoint") String knowledgePoint,
            @Param("limit") int limit);

    /** 学生薄弱知识点聚合。 */
    @Select("<script>" +
            "SELECT se.knowledge_point AS name, COUNT(*) AS error_count, " +
            "SUM(CASE WHEN se.severity IN ('critical', 'high') THEN 1 ELSE 0 END) AS critical_count, " +
            "MAX(se.created_at) AS latest_seen_at " +
            "FROM submission_errors se JOIN submission s ON s.id = se.submission_id " +
            "WHERE se.class_id = #{classId} " +
            "<choose>" +
            "<when test='studentId != null and studentId != \"\"'> AND s.student_id = #{studentId}</when>" +
            "<otherwise> AND s.student_name = #{studentName}</otherwise>" +
            "</choose> " +
            "GROUP BY se.knowledge_point ORDER BY error_count DESC LIMIT #{limit}" +
            "</script>")
    List<Map<String, Object>> selectStudentKnowledgeStats(
            @Param("classId") Long classId,
            @Param("studentId") String studentId,
            @Param("studentName") String studentName,
            @Param("limit") int limit);

    /** 学生最近错误明细，包含作业名称。 */
    @Select("<script>" +
            "SELECT se.id, se.submission_id, s.assignment_name, se.knowledge_point, " +
            "se.error_text, se.severity, se.created_at " +
            "FROM submission_errors se JOIN submission s ON s.id = se.submission_id " +
            "WHERE se.class_id = #{classId} " +
            "<choose>" +
            "<when test='studentId != null and studentId != \"\"'> AND s.student_id = #{studentId}</when>" +
            "<otherwise> AND s.student_name = #{studentName}</otherwise>" +
            "</choose> " +
            "ORDER BY se.created_at DESC LIMIT #{limit}" +
            "</script>")
    List<Map<String, Object>> selectRecentStudentErrors(
            @Param("classId") Long classId,
            @Param("studentId") String studentId,
            @Param("studentName") String studentName,
            @Param("limit") int limit);

    @Update("UPDATE submission_errors SET knowledge_point = #{to}, updated_at = NOW() " +
            "WHERE class_id = #{classId} AND knowledge_point = #{from}")
    int updateKnowledgePoint(
            @Param("classId") Long classId,
            @Param("from") String from,
            @Param("to") String to);

    @Select("SELECT COUNT(*) FROM submission_errors WHERE class_id = #{classId} AND knowledge_point = '其他'")
    int countUnclassifiedByClassId(@Param("classId") Long classId);
}
