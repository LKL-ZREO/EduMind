package com.firedemo.demo.agent.workflow;

import java.util.function.Function;

/**
 * 工作流节点 — DAG 中的单个处理步骤
 *
 * @param <S> 状态类型
 */
public interface WorkflowNode<S extends WorkflowState> {

    /** 节点唯一名称 */
    String getName();

    /** 节点描述 */
    String getDescription();

    /**
     * 执行节点逻辑
     *
     * @param state 当前工作流状态（可读写）
     * @return 执行结果（null 表示无需特殊处理，由 Edge 条件决定下一节点）
     */
    String execute(S state);

    /**
     * 失败时的回退节点（可选）
     */
    default String getFallbackNode() {
        return null;
    }

    // ==================== 工厂方法 ====================

    static <S extends WorkflowState> WorkflowNode<S> of(
            String name, String description, Function<S, String> executor) {
        return new WorkflowNode<>() {
            @Override
            public String getName() { return name; }
            @Override
            public String getDescription() { return description; }
            @Override
            public String execute(S state) { return executor.apply(state); }
        };
    }

    static <S extends WorkflowState> WorkflowNode<S> of(
            String name, String description, Function<S, String> executor, String fallbackNode) {
        return new WorkflowNode<>() {
            @Override
            public String getName() { return name; }
            @Override
            public String getDescription() { return description; }
            @Override
            public String execute(S state) { return executor.apply(state); }
            @Override
            public String getFallbackNode() { return fallbackNode; }
        };
    }
}
