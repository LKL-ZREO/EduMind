package com.firedemo.demo.Service;

import com.firedemo.demo.agent.context.AgentExecutionContext;
import reactor.core.publisher.Flux;
import java.util.List;
import java.util.Map;

/**
 * OpenClaw 服务接口
 */
public interface OpenClawService {

    /** 非流式对话 */
    String chat(String message, String status);

    String chat(String message, AgentExecutionContext context, String status);

    Flux<String> streamChat(String message, AgentExecutionContext context, String status);

    /** 流式对话（带历史消息，用于维持上下文） */
    Flux<String> streamChat(String message, List<Map<String, Object>> history,
                            AgentExecutionContext context);

    /** 注册会话的用户上下文（MCP 工具回调时用于权限过滤） */
    void registerSessionContext(AgentExecutionContext context);

    /** 健康检查 */
    boolean checkConnection();
}
