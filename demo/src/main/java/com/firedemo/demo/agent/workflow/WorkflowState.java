package com.firedemo.demo.agent.workflow;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 工作流运行状态 — 在节点之间传递的上下文
 */
@Data
public class WorkflowState {

    /** 工作流实例 ID */
    private String instanceId;

    /** 当前节点名 */
    private String currentNode;

    /** 下一个节点名（由 Edge 条件决定） */
    private String nextNode;

    /** 工作流开始时间 */
    private LocalDateTime startTime;

    /** 状态属性（线程安全，节点间共享） */
    private Map<String, Object> attributes = new ConcurrentHashMap<>();

    /** 错误信息（有值=工作流失败） */
    private String error;

    /** 是否已完成 */
    private boolean completed;

    // ==================== 便捷方法 ====================

    @SuppressWarnings("unchecked")
    public <T> T getAttr(String key) {
        return (T) attributes.get(key);
    }

    public void setAttr(String key, Object value) {
        attributes.put(key, value);
    }

    public String getStringAttr(String key) {
        Object v = attributes.get(key);
        return v != null ? v.toString() : null;
    }

    public static WorkflowState create(String instanceId) {
        WorkflowState s = new WorkflowState();
        s.instanceId = instanceId;
        s.startTime = LocalDateTime.now();
        return s;
    }
}
