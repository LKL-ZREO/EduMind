package com.firedemo.demo.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.firedemo.demo.Entity.ClassInfo;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;
import java.util.Map;

/**
 * 班级信息Mapper
 */
@Mapper
public interface ClassInfoMapper extends BaseMapper<ClassInfo> {

    /**
     * 查询教师管理的班级列表
     */
    @Select("SELECT * FROM class_info WHERE teacher_id = #{teacherId}")
    List<ClassInfo> selectByTeacherId(Long teacherId);

    /**
     * 查询班级学生数量
     */
    @Select("SELECT COUNT(*) FROM sys_user WHERE class_id = #{classId} AND status = 1")
    Integer selectStudentCount(Long classId);

    /**
     * 查询教师管理的班级列表（含学生数，一次 JOIN 替代 N+1）
     */
    @Select("SELECT ci.id, ci.name, ci.description, ci.status, ci.course_group, " +
            "ci.teacher_id, ci.created_at, ci.invite_code, " +
            "COUNT(u.id) as student_count " +
            "FROM class_info ci " +
            "LEFT JOIN sys_user u ON u.class_id = ci.id AND u.status = 1 " +
            "WHERE ci.teacher_id = #{teacherId} " +
            "GROUP BY ci.id")
    List<Map<String, Object>> selectByTeacherIdWithStudentCount(@Param("teacherId") Long teacherId);

    /**
     * 根据班级ID查询QQ群号
     */
    @Select("SELECT qq_group_id FROM class_info WHERE id = #{classId}")
    String selectQqGroupIdById(Long classId);

    /**
     * 查询所有班级ID（布隆过滤器初始化用）
     */
    @Select("SELECT id FROM class_info")
    List<Long> selectAllIds();
}
