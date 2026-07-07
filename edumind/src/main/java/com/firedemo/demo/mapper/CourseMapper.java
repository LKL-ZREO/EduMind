package com.firedemo.demo.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.firedemo.demo.Entity.Course;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface CourseMapper extends BaseMapper<Course> {

    @Select("SELECT * FROM course WHERE teacher_id = #{teacherId} ORDER BY updated_at DESC")
    List<Course> selectByTeacherId(@Param("teacherId") Long teacherId);

    @Select("SELECT c.* FROM course c " +
            "INNER JOIN class_info ci ON ci.course_id = c.id " +
            "WHERE ci.id = #{classId}")
    Course selectByClassId(@Param("classId") Long classId);
}
