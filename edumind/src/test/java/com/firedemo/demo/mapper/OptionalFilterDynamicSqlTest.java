package com.firedemo.demo.mapper;

import org.apache.ibatis.mapping.BoundSql;
import org.apache.ibatis.session.Configuration;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class OptionalFilterDynamicSqlTest {

    private final Configuration configuration = mapperConfiguration();

    @Test
    void omitsCoursePredicateWhenCourseScopeIsAbsent() {
        Map<String, Object> parameters = new HashMap<>();
        parameters.put("name", "Computer Class 1");
        parameters.put("teacherId", 101L);
        parameters.put("courseId", null);

        BoundSql boundSql = boundSql(ClassInfoMapper.class, "selectOwnedByName", parameters);

        assertThat(normalize(boundSql.getSql()))
                .isEqualTo("SELECT * FROM class_info WHERE name = ? AND teacher_id = ? LIMIT 1");
        assertThat(boundSql.getParameterMappings()).hasSize(2);
    }

    @Test
    void includesCoursePredicateWhenCourseScopeIsPresent() {
        Map<String, Object> parameters = Map.of(
                "name", "Computer Class 1",
                "teacherId", 101L,
                "courseId", 7L);

        BoundSql boundSql = boundSql(ClassInfoMapper.class, "selectOwnedByName", parameters);

        assertThat(normalize(boundSql.getSql()))
                .isEqualTo("SELECT * FROM class_info WHERE name = ? AND teacher_id = ? AND course_id = ? LIMIT 1");
        assertThat(boundSql.getParameterMappings()).hasSize(3);
    }

    @Test
    void omitsQuestionKeywordPredicateWhenKeywordIsAbsent() {
        Map<String, Object> parameters = new HashMap<>();
        parameters.put("teacherId", 101L);
        parameters.put("keyword", null);

        BoundSql boundSql = boundSql(QuestionBankItemMapper.class, "searchByTeacher", parameters);

        assertThat(normalize(boundSql.getSql()))
                .isEqualTo("SELECT * FROM question_bank_item WHERE teacher_id = ? "
                        + "AND archived = FALSE ORDER BY updated_at DESC LIMIT 200");
        assertThat(boundSql.getParameterMappings()).hasSize(1);
    }

    @Test
    void includesQuestionKeywordPredicateWhenKeywordIsPresent() {
        Map<String, Object> parameters = Map.of(
                "teacherId", 101L,
                "keyword", "polymorphism");

        BoundSql boundSql = boundSql(QuestionBankItemMapper.class, "searchByTeacher", parameters);

        assertThat(normalize(boundSql.getSql()))
                .contains("title ILIKE CONCAT('%', ?, '%')")
                .contains("requirement ILIKE CONCAT('%', ?, '%')")
                .contains("knowledge_point ILIKE CONCAT('%', ?, '%')");
        assertThat(boundSql.getParameterMappings()).hasSize(4);
    }

    @Test
    void scopesRecentChatHistoryByUserAndRestoresChronologicalOrder() {
        Map<String, Object> parameters = Map.of(
                "userId", 42L,
                "sessionId", "session-1",
                "limit", 10);

        BoundSql boundSql = boundSql(ChatHistoryMapper.class, "selectBySessionId", parameters);

        assertThat(normalize(boundSql.getSql()))
                .isEqualTo("SELECT * FROM ( SELECT * FROM chat_history "
                        + "WHERE user_id = ? AND session_id = ? "
                        + "ORDER BY created_at DESC, id DESC LIMIT ? ) recent "
                        + "ORDER BY created_at ASC, id ASC");
        assertThat(boundSql.getParameterMappings()).hasSize(3);
    }

    private BoundSql boundSql(Class<?> mapperType, String method, Map<String, Object> parameters) {
        String statementId = mapperType.getName() + "." + method;
        return configuration.getMappedStatement(statementId).getBoundSql(parameters);
    }

    private Configuration mapperConfiguration() {
        Configuration mybatis = new Configuration();
        mybatis.addMapper(ClassInfoMapper.class);
        mybatis.addMapper(QuestionBankItemMapper.class);
        mybatis.addMapper(ChatHistoryMapper.class);
        return mybatis;
    }

    private String normalize(String sql) {
        return sql.replaceAll("\\s+", " ").trim();
    }
}
