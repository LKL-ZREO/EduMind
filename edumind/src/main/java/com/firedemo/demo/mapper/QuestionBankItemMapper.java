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
            <script>
            SELECT *
            FROM question_bank_item
            WHERE teacher_id = #{teacherId}
            <if test="keyword != null and keyword != ''">
              AND (
                title ILIKE CONCAT('%', #{keyword}, '%')
                OR requirement ILIKE CONCAT('%', #{keyword}, '%')
              )
            </if>
            ORDER BY updated_at DESC
            LIMIT 200
            </script>
            """)
    List<QuestionBankItem> searchByTeacher(
            @Param("teacherId") Long teacherId,
            @Param("keyword") String keyword
    );
}
