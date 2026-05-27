package com.firedemo.demo.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.firedemo.demo.Entity.SubmissionError;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

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
     * 查询某个班级所有未归类的错误（用于重分类）
     */
    @Select("SELECT * FROM submission_errors " +
            "WHERE class_id = #{classId} AND knowledge_point = '其他' " +
            "ORDER BY created_at ASC")
    List<SubmissionError> selectUnclassifiedByClassId(@Param("classId") Long classId);
}
