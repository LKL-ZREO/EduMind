package com.firedemo.demo.agent.langchain4j;

import com.firedemo.demo.agent.context.AgentChannel;
import com.firedemo.demo.agent.context.AgentExecutionContext;
import com.firedemo.demo.agent.context.AgentRunTrace;
import com.firedemo.demo.mcp.ToolDefinition;
import dev.langchain4j.agent.tool.ToolSpecifications;
import dev.langchain4j.invocation.InvocationParameters;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;

class LangChain4jToolBridgeTest {

    @Test
    void trustedInvocationParametersAreExcludedFromTheModelToolSchema() {
        LangChain4jToolBridge bridge = new LangChain4jToolBridge(List.of());

        var searchSpecification = ToolSpecifications.toolSpecificationsFrom(bridge).stream()
                .filter(specification -> "searchKnowledge".equals(specification.name()))
                .findFirst()
                .orElseThrow();

        assertThat(searchSpecification.parameters().properties())
                .containsOnlyKeys("query", "topK");
    }

    @Test
    void passesTrustedInvocationContextWithoutAddingItToModelArguments() throws Exception {
        ToolDefinition tool = new ToolDefinition() {
            @Override public String name() { return "searchKnowledge"; }
            @Override public String description() { return "test"; }
            @Override public Map<String, Object> inputSchema() { return Map.of(); }
            @Override
            public String execute(Map<String, Object> arguments, AgentExecutionContext context) {
                assertThat(arguments).containsOnlyKeys("query", "topK");
                return context.userId() + ":" + context.traceId();
            }
        };
        LangChain4jToolBridge bridge = new LangChain4jToolBridge(List.of(tool));
        AgentExecutionContext first = context("first", 101L);
        AgentExecutionContext second = context("second", 202L);
        AgentRunTrace firstTrace = new AgentRunTrace(first);
        AgentRunTrace secondTrace = new AgentRunTrace(second);

        try (ExecutorService executor = Executors.newFixedThreadPool(2)) {
            Future<String> firstResult = executor.submit(() -> bridge.searchKnowledge(
                    "pointer", 3, parameters(first, firstTrace)));
            Future<String> secondResult = executor.submit(() -> bridge.searchKnowledge(
                    "array", 5, parameters(second, secondTrace)));

            assertThat(firstResult.get()).isEqualTo("101:trace-first");
            assertThat(secondResult.get()).isEqualTo("202:trace-second");
        }

        assertThat(firstTrace.toolCallCount()).isEqualTo(1);
        assertThat(secondTrace.toolCallCount()).isEqualTo(1);
    }

    private InvocationParameters parameters(AgentExecutionContext context, AgentRunTrace trace) {
        return AgentInvocationParameters.create(context, trace);
    }

    private AgentExecutionContext context(String sessionId, Long userId) {
        return new AgentExecutionContext(
                sessionId, userId, 1L, Set.of(1L), AgentChannel.WEB, "trace-" + sessionId);
    }
}
