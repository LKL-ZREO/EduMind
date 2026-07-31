package com.firedemo.edumind.integration.mcp;

import com.firedemo.edumind.assistant.tool.ToolAccessPolicy;
import com.firedemo.edumind.classroom.ClassInfo;
import com.firedemo.edumind.assistant.context.AgentChannel;
import com.firedemo.edumind.assistant.context.AgentExecutionContext;
import com.firedemo.edumind.classroom.ClassInfoMapper;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class ToolAccessPolicyTest {

    @Test
    void scopesClassLookupByNameTeacherAndCourse() {
        ClassInfoMapper mapper = mock(ClassInfoMapper.class);
        ClassInfo owned = classInfo(1L, 101L, 11L);
        when(mapper.selectOwnedByName("C101", 101L, 11L)).thenReturn(owned);
        ToolAccessPolicy policy = new ToolAccessPolicy(mapper);

        ClassInfo result = policy.findOwnedClass(context(101L, 11L), "C101");

        assertThat(result).isSameAs(owned);
        verify(mapper).selectOwnedByName("C101", 101L, 11L);
    }

    @Test
    void filtersAccessibleClassesToTheCurrentCourse() {
        ClassInfoMapper mapper = mock(ClassInfoMapper.class);
        when(mapper.selectByTeacherId(101L)).thenReturn(List.of(
                classInfo(1L, 101L, 11L),
                classInfo(2L, 101L, 22L)));
        ToolAccessPolicy policy = new ToolAccessPolicy(mapper);

        assertThat(policy.accessibleClassIds(context(101L, 11L))).containsExactly(1L);
    }

    @Test
    void anonymousContextDoesNotQueryClassData() {
        ClassInfoMapper mapper = mock(ClassInfoMapper.class);
        ToolAccessPolicy policy = new ToolAccessPolicy(mapper);
        AgentExecutionContext anonymous = new AgentExecutionContext(
                "anonymous", null, null, Set.of(), AgentChannel.MCP, "trace");

        assertThat(policy.findOwnedClass(anonymous, "C101")).isNull();
        assertThat(policy.accessibleClassIds(anonymous)).isEmpty();
        verifyNoInteractions(mapper);
    }

    private AgentExecutionContext context(Long userId, Long courseId) {
        return new AgentExecutionContext(
                "session", userId, courseId, Set.of(), AgentChannel.WEB, "trace");
    }

    private ClassInfo classInfo(Long id, Long teacherId, Long courseId) {
        ClassInfo classInfo = new ClassInfo();
        classInfo.setId(id);
        classInfo.setTeacherId(teacherId);
        classInfo.setCourseId(courseId);
        return classInfo;
    }
}
