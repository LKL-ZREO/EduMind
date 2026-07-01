package com.firedemo.demo.mcp;

import java.util.Collections;
import java.util.Set;

/**
 * MCP 工具执行时的用户上下文。工具通过 {@link ToolContextHolder} 获取。
 */
public class ToolContext {

    private final Long userId;
    private final Set<Long> accessibleKbIds;
    private final Long courseId;

    public ToolContext(Long userId, Set<Long> accessibleKbIds) {
        this(userId, accessibleKbIds, null);
    }

    public ToolContext(Long userId, Set<Long> accessibleKbIds, Long courseId) {
        this.userId = userId;
        this.accessibleKbIds = accessibleKbIds != null ? Set.copyOf(accessibleKbIds) : Collections.emptySet();
        this.courseId = courseId;
    }

    public Long getUserId() { return userId; }
    public Set<Long> getAccessibleKbIds() { return accessibleKbIds; }
    public Long getCourseId() { return courseId; }

    @Override
    public String toString() {
        return "ToolContext{userId=" + userId + ", kbCount="
                + accessibleKbIds.size() + ", courseId=" + courseId + "}";
    }
}
