package com.firedemo.demo.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.firedemo.demo.Entity.TeacherKnowledge;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 教师自定义知识点 Mapper
 */
@Mapper
public interface TeacherKnowledgeMapper extends BaseMapper<TeacherKnowledge> {

    /**
     * 查询班级的所有自定义知识点（按排序号）
     */
    @Select("SELECT * FROM teacher_knowledge WHERE class_id = #{classId} ORDER BY sort_order ASC, id ASC")
    List<TeacherKnowledge> selectByClassId(@Param("classId") Long classId);

    /**
     * 判断某个知识点名称在班级中是否已存在
     */
    @Select("SELECT COUNT(*) > 0 FROM teacher_knowledge WHERE class_id = #{classId} AND name = #{name}")
    boolean exists(@Param("classId") Long classId, @Param("name") String name);
}
