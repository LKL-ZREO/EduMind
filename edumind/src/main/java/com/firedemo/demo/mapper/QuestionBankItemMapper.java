package com.firedemo.demo.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.firedemo.demo.Entity.QuestionBankItem;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

@Mapper
public interface QuestionBankItemMapper extends BaseMapper<QuestionBankItem> {

    @Select("""
            <script>
            SELECT *
            FROM question_bank_item
            WHERE teacher_id = #{teacherId}
              AND archived = FALSE
            <if test="keyword != null and keyword != ''">
              AND (
                title ILIKE CONCAT('%', #{keyword}, '%')
                OR requirement ILIKE CONCAT('%', #{keyword}, '%')
                OR knowledge_point ILIKE CONCAT('%', #{keyword}, '%')
              )
            </if>
            <if test="sourceDocId != null and sourceDocId != ''">
              AND source_doc_id = #{sourceDocId}
            </if>
            <if test="type != null and type != ''">
              AND type = #{type}
            </if>
            ORDER BY updated_at DESC
            LIMIT 200
            </script>
            """)
    List<QuestionBankItem> searchByTeacher(
            @Param("teacherId") Long teacherId,
            @Param("keyword") String keyword,
            @Param("sourceDocId") String sourceDocId,
            @Param("type") String type
    );

    @Insert("""
            INSERT INTO question_bank_item (
                teacher_id, type, title, requirement, options, correct_key, explanation,
                knowledge_point, difficulty, default_time_limit, score, upload_required,
                source_doc_id, ai_generated, archived, created_at, updated_at
            ) VALUES (
                #{teacherId}, #{type}, #{title}, #{requirement}, CAST(#{options} AS jsonb),
                #{correctKey}, #{explanation}, #{knowledgePoint}, #{difficulty},
                #{defaultTimeLimit}, #{score}, #{uploadRequired}, #{sourceDocId},
                #{aiGenerated}, #{archived}, #{createdAt}, #{updatedAt}
            )
            """)
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insertWithJsonb(QuestionBankItem item);

    @Update("""
            UPDATE question_bank_item
            SET type = #{type}, title = #{title}, requirement = #{requirement},
                options = CAST(#{options} AS jsonb), correct_key = #{correctKey},
                explanation = #{explanation}, knowledge_point = #{knowledgePoint},
                difficulty = #{difficulty}, default_time_limit = #{defaultTimeLimit},
                score = #{score}, upload_required = #{uploadRequired},
                source_doc_id = #{sourceDocId}, ai_generated = #{aiGenerated},
                archived = #{archived}, updated_at = #{updatedAt}
            WHERE id = #{id} AND teacher_id = #{teacherId}
            """)
    int updateOwnedWithJsonb(QuestionBankItem item);

    @Update("""
            UPDATE question_bank_item
            SET archived = TRUE, updated_at = CURRENT_TIMESTAMP
            WHERE id = #{id} AND teacher_id = #{teacherId} AND archived = FALSE
            """)
    int archiveOwned(@Param("id") Long id, @Param("teacherId") Long teacherId);
}
