package com.firedemo.demo.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.firedemo.demo.Entity.User;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface UserMapper extends BaseMapper<User> {

    /**
     * 查询班级的所有学生
     */
    @Select("SELECT * FROM sys_user WHERE class_id = #{classId} AND status = 1")
    List<User> selectStudentsByClassId(@Param("classId") Long classId);

    /**
     * 查询班级学生数量
     */
    @Select("SELECT COUNT(*) FROM sys_user WHERE class_id = #{classId} AND status = 1")
    Integer countStudentsByClassId(@Param("classId") Long classId);
}
