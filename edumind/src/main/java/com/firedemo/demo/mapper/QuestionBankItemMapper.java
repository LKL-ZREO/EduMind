package com.firedemo.demo.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.firedemo.demo.Entity.QuestionBankItem;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface QuestionBankItemMapper extends BaseMapper<QuestionBankItem> {

    @Select("""
        SELECT *
        FROM question_bank_item
        WHERE teacher_id = #{teacherId}
          AND (
            #{keyword} IS NULL
            OR #{keyword} = ''
            OR title ILIKE CONCAT('%', #{keyword}, '%')
            OR requirement ILIKE CONCAT('%', #{keyword}, '%')
          )
        ORDER BY updated_at DESC
        LIMIT 200
    """)
    List<QuestionBankItem> searchByTeacher(
            @Param("teacherId") Long teacherId,
            @Param("keyword") String keyword
    );
}
