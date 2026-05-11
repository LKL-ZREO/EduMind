package com.firedemo.demo.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.firedemo.demo.Entity.ClassInfo;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

import java.util.List;

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
     * 根据班级ID查询QQ群号
     */
    @Select("SELECT qq_group_id FROM class_info WHERE id = #{classId}")
    String selectQqGroupIdById(Long classId);
}
