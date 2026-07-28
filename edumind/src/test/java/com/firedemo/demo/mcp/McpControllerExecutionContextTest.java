package com.firedemo.demo.mcp;

import com.firedemo.demo.agent.context.AgentChannel;
import com.firedemo.demo.agent.context.AgentExecutionContext;
import com.firedemo.demo.agent.context.AgentExecutionContextFactory;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class McpControllerExecutionContextTest {

    @Test
    void resolvesHeaderSessionAndPassesTrustedContextToTheTool() {
        AgentExecutionContext context = new AgentExecutionContext(
                "session-1", 10L, 20L, Set.of(30L), AgentChannel.MCP, "trace-1");
        AtomicReference<AgentExecutionContext> received = new AtomicReference<>();
        ToolDefinition tool = tool(received);
        McpSessionStore sessionStore = mock(McpSessionStore.class);
        AgentExecutionContextFactory contextFactory = mock(AgentExecutionContextFactory.class);
        HttpServletRequest servletRequest = mock(HttpServletRequest.class);
        when(servletRequest.getHeader("X-Session-Id")).thenReturn("session-1");
        when(sessionStore.resolve("session-1", AgentChannel.MCP)).thenReturn(context);
        McpController controller = new McpController(List.of(tool), sessionStore, contextFactory);

        Map<String, Object> response = controller.handle(Map.of(
                "jsonrpc", "2.0",
                "id", 1,
                "method", "tools/call",
                "params", Map.of(
                        "name", "testTool",
                        "arguments", Map.of("value", "ok"))), servletRequest);

        assertThat(received.get()).isSameAs(context);
        assertThat(response).containsEntry("jsonrpc", "2.0").containsKey("result");
    }

    private ToolDefinition tool(AtomicReference<AgentExecutionContext> received) {
        return new ToolDefinition() {
            @Override public String name() { return "testTool"; }
            @Override public String description() { return "test"; }
            @Override public Map<String, Object> inputSchema() { return Map.of(); }
            @Override
            public String execute(Map<String, Object> arguments, AgentExecutionContext context) {
                received.set(context);
                return String.valueOf(arguments.get("value"));
            }
        };
    }
}
