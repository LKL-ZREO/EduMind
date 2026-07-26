package com.firedemo.demo.mcp;

import com.firedemo.demo.agent.context.AgentChannel;
import com.firedemo.demo.agent.context.AgentExecutionContext;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class ToolDefinitionExecutionContextTest {

    @Test
    void explicitContractReceivesTrustedContextSeparatelyFromModelArguments() {
        AtomicReference<Map<String, Object>> received = new AtomicReference<>();
        AtomicReference<AgentExecutionContext> receivedContext = new AtomicReference<>();
        ToolDefinition tool = explicitTool(received, receivedContext);
        AgentExecutionContext context = new AgentExecutionContext(
                "session", 10L, null, Set.of(), AgentChannel.WEB, "trace");
        Map<String, Object> arguments = Map.of("query", "pointer");

        String result = tool.execute(arguments, context);

        assertThat(result).isEqualTo("ok");
        assertThat(received.get()).isSameAs(arguments);
        assertThat(receivedContext.get()).isSameAs(context);
    }

    private ToolDefinition explicitTool(AtomicReference<Map<String, Object>> received,
                                        AtomicReference<AgentExecutionContext> receivedContext) {
        return new ToolDefinition() {
            @Override public String name() { return "test"; }
            @Override public String description() { return "test"; }
            @Override public Map<String, Object> inputSchema() { return Map.of(); }
            @Override
            public String execute(Map<String, Object> arguments, AgentExecutionContext context) {
                received.set(arguments);
                receivedContext.set(context);
                return "ok";
            }
        };
    }
}
