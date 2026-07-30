package com.firedemo.demo.Service;

import com.firedemo.demo.agent.context.AgentExecutionContext;
import reactor.core.publisher.Flux;

/**
 * OpenClaw 服务接口
 */
public interface OpenClawService {

    /** 非流式对话 */
    String chat(String message, String status);

    String chat(String message, AgentExecutionContext context, String status);

    Flux<String> streamChat(String message, AgentExecutionContext context, String status);

    /** 注册会话的用户上下文（MCP 工具回调时用于权限过滤） */
    void registerSessionContext(AgentExecutionContext context);

    /** 清除该用户的全部 Agent 工作记忆。 */
    void clearMemory(Long userId);

    /** 清理单个用户会话的工作记忆。 */
    void clearMemory(Long userId, String sessionId);

    /** 健康检查 */
    boolean checkConnection();
}
