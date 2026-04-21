package com.firedemo.demo.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.firedemo.demo.Entity.HomeworkKnowledge;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;
import java.util.Map;

/**
 * 作业知识点Mapper
 */
@Mapper
public interface HomeworkKnowledgeMapper extends BaseMapper<HomeworkKnowledge> {

    /**
     * 查询班级的知识点掌握度统计
     */
    @Select("SELECT hk.knowledge_point, AVG(hk.mastery) as avg_mastery, COUNT(*) as count " +
            "FROM homework_knowledge hk " +
            "INNER JOIN homework_evaluation he ON hk.evaluation_id = he.id " +
            "WHERE he.class_id = #{classId} " +
            "GROUP BY hk.knowledge_point " +
            "ORDER BY avg_mastery ASC")
    List<Map<String, Object>> selectKnowledgeStatsByClassId(@Param("classId") Long classId);

    /**
     * 查询班级的薄弱知识点（掌握度<70）
     */
    @Select("SELECT DISTINCT knowledge_point " +
            "FROM homework_knowledge hk " +
            "JOIN homework_evaluation he ON hk.evaluation_id = he.id " +
            "WHERE he.class_id = #{classId} AND hk.mastery < 70")
    List<String> selectWeakKnowledgePoints(@Param("classId") Long classId);
}
