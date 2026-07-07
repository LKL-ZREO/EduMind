package com.firedemo.demo.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.firedemo.demo.Entity.HomeworkTask;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface HomeworkTaskMapper extends BaseMapper<HomeworkTask> {

    @Select("SELECT * FROM homework_task WHERE class_id = #{classId} ORDER BY created_at DESC")
    List<HomeworkTask> selectByClassId(@Param("classId") Long classId);

    @Select("SELECT * FROM homework_task WHERE status != 'closed' AND deadline IS NOT NULL AND deadline > NOW() ORDER BY deadline ASC")
    List<HomeworkTask> selectActiveWithDeadline();
}
