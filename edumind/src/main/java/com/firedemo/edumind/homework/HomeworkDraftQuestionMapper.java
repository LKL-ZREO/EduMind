package com.firedemo.edumind.homework;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface HomeworkDraftQuestionMapper extends BaseMapper<HomeworkDraftQuestion> {

    @Select("""
        SELECT *
        FROM homework_draft_question
        WHERE draft_id = #{draftId}
        ORDER BY sort_order ASC, id ASC
    """)
    List<HomeworkDraftQuestion> selectByDraftId(@Param("draftId") Long draftId);

    @Delete("DELETE FROM homework_draft_question WHERE draft_id = #{draftId}")
    void deleteByDraftId(@Param("draftId") Long draftId);
}
