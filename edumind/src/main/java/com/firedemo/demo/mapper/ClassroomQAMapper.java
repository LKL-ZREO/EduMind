package com.firedemo.demo.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.firedemo.demo.Entity.ClassroomQA;
import org.apache.ibatis.annotations.*;
import java.util.List;

@Mapper
public interface ClassroomQAMapper extends BaseMapper<ClassroomQA> {
    @Select("SELECT * FROM classroom_qa WHERE session_id = #{sessionId} AND similar_to IS NULL ORDER BY similar_count DESC, created_at DESC")
    List<ClassroomQA> findTopLevelBySessionId(@Param("sessionId") Long sessionId);
    @Update("UPDATE classroom_qa SET is_answered = TRUE, answer_text = #{answerText} WHERE id = #{id}")
    int markAnswered(@Param("id") Long id, @Param("answerText") String answerText);
    @Insert("INSERT INTO classroom_qa (session_id, question, student_id, student_name, is_answered, similar_to, similar_count, created_at) " +
            "VALUES (#{sessionId}, #{question}, #{studentId}, #{studentName}, FALSE, #{similarTo}, 0, NOW())")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insertQuestion(ClassroomQA qa);
}
