package com.firedemo.demo.agent.workflow;

import lombok.extern.slf4j.Slf4j;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.stereotype.Component;

/**
 * 工作流执行引擎 — 按 DAG 拓扑顺序执行节点
 * <p>
 * 特性：
 * <ul>
 *   <li>DAG 拓扑执行，避免循环</li>
 *   <li>节点失败自动跳转 fallbackNode</li>
 *   <li>执行轨迹可追溯</li>
 *   <li>Thread-safe</li>
 * </ul>
 */
@Slf4j
@Component
public class WorkflowEngine {

    /** 最大执行步数（防死循环） */
    private static final int MAX_STEPS = 50;

    /** 保存工作流执行轨迹 */
    private final Map<String, List<String>> traces = new ConcurrentHashMap<>();

    /**
     * 执行工作流
     *
     * @param definition  工作流定义
     * @param initialState 初始状态
     * @param <S>         状态类型
     * @return 执行后的状态
     */
    public <S extends WorkflowState> S execute(WorkflowDefinition<S> definition, S initialState) {
        S state = initialState;
        state.setCurrentNode(definition.getEntryNode());

        WorkflowNode<S> node = definition.getNodes().get(definition.getEntryNode());
        if (node == null) {
            state.setError("入口节点不存在: " + definition.getEntryNode());
            return state;
        }

        List<String> trace = new ArrayList<>();
        int step = 0;

        while (node != null && step < MAX_STEPS) {
            step++;
            trace.add(node.getName());
            log.info("Workflow [{}] step {}: node={}", definition.getName(), step, node.getName());

            try {
                // 执行当前节点
                String result = node.execute(state);
                state.setCurrentNode(node.getName());

                // 检查是否应该停止
                if (state.isCompleted()) {
                    log.info("Workflow [{}] 已完成", definition.getName());
                    break;
                }

                // 查找下一个节点
                String next = resolveNextNode(definition, node.getName(), state);
                if (next == null) {
                    log.info("Workflow [{}] 无可达节点，结束", definition.getName());
                    break;
                }

                node = definition.getNodes().get(next);
                state.setNextNode(next);
                state.setCurrentNode(next);

            } catch (Exception e) {
                log.error("Workflow [{}] 节点 [{}] 执行失败: {}", definition.getName(), node.getName(), e.getMessage(), e);
                String fallback = node.getFallbackNode();
                if (fallback != null && definition.getNodes().containsKey(fallback)) {
                    log.info("跳转 fallback 节点: {}", fallback);
                    node = definition.getNodes().get(fallback);
                    state.setAttr("lastError", e.getMessage());
                    trace.add("(fallback)→" + fallback);
                } else {
                    state.setError(e.getMessage());
                    break;
                }
            }
        }

        if (step >= MAX_STEPS) {
            state.setError("工作流超过最大步数限制 (" + MAX_STEPS + ")，可能存在循环");
        }

        traces.put(state.getInstanceId(), trace);
        return state;
    }

    /**
     * 获取执行轨迹
     */
    public List<String> getTrace(String instanceId) {
        return traces.getOrDefault(instanceId, Collections.emptyList());
    }

    // ==================== 私有方法 ====================

    private <S extends WorkflowState> String resolveNextNode(
            WorkflowDefinition<S> definition, String currentNode, S state) {

        List<WorkflowDefinition.Edge<S>> edges = definition.getEdges().get(currentNode);
        if (edges == null || edges.isEmpty()) {
            return null; // 无出口 = 终点
        }

        // 按顺序评估条件，返回第一个匹配的
        for (WorkflowDefinition.Edge<S> edge : edges) {
            String condResult = edge.getCondition().apply(state);
            if (WorkflowDefinition.Edge.ALWAYS.equals(condResult)
                    || edge.getTarget().equals(condResult)) {
                return edge.getTarget();
            }
        }

        return null;
    }
}
