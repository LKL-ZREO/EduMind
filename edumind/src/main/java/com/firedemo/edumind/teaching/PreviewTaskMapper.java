package com.firedemo.edumind.teaching;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

@Mapper
public interface PreviewTaskMapper extends BaseMapper<PreviewTask> {

    @Insert("INSERT INTO preview_task (class_id, teacher_id, source_doc_id, title, knowledge_point, guide_text, questions_json, discussion_question, status, created_at, updated_at) " +
            "VALUES (#{classId}, #{teacherId}, #{sourceDocId}, #{title}, #{knowledgePoint}, #{guideText}, CAST(#{questionsJson} AS jsonb), #{discussionQuestion}, #{status}, NOW(), NOW())")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insertWithJsonb(PreviewTask task);

    @Select("SELECT * FROM preview_task WHERE class_id = #{classId} AND status = 'ACTIVE' ORDER BY created_at DESC")
    List<PreviewTask> findByClassId(@Param("classId") Long classId);

    @Select("SELECT * FROM preview_task WHERE id = #{id}")
    PreviewTask findById(@Param("id") Long id);

    @Update("UPDATE preview_task SET status = 'CLOSED', updated_at = NOW() WHERE id = #{id}")
    int closeTask(@Param("id") Long id);

    @Select("SELECT * FROM preview_task WHERE source_doc_id = #{docId} ORDER BY created_at DESC")
    List<PreviewTask> findBySourceDocId(@Param("docId") String docId);
}
