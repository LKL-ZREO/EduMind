package com.firedemo.demo.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Insert;

/**
 * 学生QQ绑定Mapper
 */
@Mapper
public interface StudentQqBindingMapper {

    /**
     * 根据学号查询QQ号
     */
    @Select("SELECT qq_number FROM student_qq_binding WHERE student_id = #{studentId}")
    String selectQqByStudentId(@Param("studentId") String studentId);

    /**
     * 插入或更新绑定关系
     */
    @Insert("INSERT INTO student_qq_binding (student_id, qq_number, student_name, created_at) " +
            "VALUES (#{studentId}, #{qqNumber}, #{studentName}, NOW()) " +
            "ON CONFLICT (student_id) DO UPDATE SET " +
            "qq_number = EXCLUDED.qq_number, student_name = EXCLUDED.student_name")
    void insertOrUpdate(@Param("studentId") String studentId,
                        @Param("qqNumber") String qqNumber,
                        @Param("studentName") String studentName);
}
