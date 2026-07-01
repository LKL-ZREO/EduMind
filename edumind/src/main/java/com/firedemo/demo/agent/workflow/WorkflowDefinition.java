package com.firedemo.demo.agent.workflow;

import lombok.Data;

import java.util.*;
import java.util.function.Function;

/**
 * 工作流定义 — 有向无环图（DAG）
 * <p>
 * 定义节点集合和边（转移条件），由 {@link WorkflowEngine} 执行。
 *
 * @param <S> 状态类型
 */
@Data
public class WorkflowDefinition<S extends WorkflowState> {

    /** 工作流名称 */
    private String name;

    /** 描述 */
    private String description;

    /** 入口节点名 */
    private String entryNode;

    /** 所有节点（key=节点名） */
    private Map<String, WorkflowNode<S>> nodes = new LinkedHashMap<>();

    /** 边的定义：源节点 → (目标节点, 转移条件) */
    private Map<String, List<Edge<S>>> edges = new LinkedHashMap<>();

    public WorkflowDefinition<S> addNode(WorkflowNode<S> node) {
        nodes.put(node.getName(), node);
        return this;
    }

    public WorkflowDefinition<S> addEdge(String from, String to, Function<S, String> condition) {
        edges.computeIfAbsent(from, k -> new ArrayList<>())
                .add(new Edge<>(to, condition));
        return this;
    }

    /**
     * 无条件转移
     */
    public WorkflowDefinition<S> addEdge(String from, String to) {
        return addEdge(from, to, s -> Edge.ALWAYS);
    }

    @Data
    public static class Edge<S extends WorkflowState> {
        public static final String ALWAYS = "__ALWAYS__";

        private String target;
        private Function<S, String> condition;

        public Edge(String target, Function<S, String> condition) {
            this.target = target;
            this.condition = condition;
        }
    }
}
