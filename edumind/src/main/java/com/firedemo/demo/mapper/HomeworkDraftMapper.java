package com.firedemo.demo.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.firedemo.demo.Entity.HomeworkDraft;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface HomeworkDraftMapper extends BaseMapper<HomeworkDraft> {

    @Select("""
        SELECT *
        FROM homework_draft
        WHERE teacher_id = #{teacherId}
        ORDER BY updated_at DESC
    """)
    List<HomeworkDraft> selectByTeacherId(@Param("teacherId") Long teacherId);
}
