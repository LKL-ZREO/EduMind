package com.firedemo.demo.mcp;

import com.firedemo.demo.Entity.ClassInfo;
import com.firedemo.demo.agent.context.AgentExecutionContext;
import com.firedemo.demo.mapper.ClassInfoMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

/** Applies the authenticated teacher and course scope to agent data tools. */
@Component
@RequiredArgsConstructor
public class ToolAccessPolicy {

    private final ClassInfoMapper classInfoMapper;

    public ClassInfo findOwnedClass(AgentExecutionContext context, String className) {
        if (!context.isAuthenticated() || className == null || className.isBlank()) {
            return null;
        }
        return classInfoMapper.selectOwnedByName(
                className, context.userId(), context.courseId());
    }

    public List<Long> accessibleClassIds(AgentExecutionContext context) {
        if (!context.isAuthenticated()) {
            return List.of();
        }
        List<ClassInfo> classes = classInfoMapper.selectByTeacherId(context.userId());
        if (classes == null || classes.isEmpty()) {
            return List.of();
        }
        return classes.stream()
                .filter(classInfo -> context.courseId() == null
                        || context.courseId().equals(classInfo.getCourseId()))
                .map(ClassInfo::getId)
                .toList();
    }
}
