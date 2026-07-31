package com.firedemo.edumind.assistant.langchain4j;

import com.firedemo.edumind.assistant.context.AgentChannel;
import com.firedemo.edumind.assistant.context.AgentExecutionContext;
import com.firedemo.edumind.assistant.context.AgentRunTrace;
import com.firedemo.edumind.assistant.observability.AgentToolMetrics;
import com.firedemo.edumind.assistant.tool.ToolDefinition;
import dev.langchain4j.agent.tool.ToolSpecifications;
import dev.langchain4j.invocation.InvocationParameters;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
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
        LangChain4jToolBridge bridge = new LangChain4jToolBridge(
                List.of(), new AgentToolMetrics(new SimpleMeterRegistry()));

        var searchSpecification = ToolSpecifications.toolSpecificationsFrom(bridge).stream()
                .filter(specification -> "searchKnowledge".equals(specification.name()))
                .findFirst()
                .orElseThrow();

        assertThat(searchSpecification.parameters().properties())
                .containsOnlyKeys("query", "topK");
    }

    @Test
    void passesTrustedInvocationContextWithoutAddingItToModelArguments() throws Exception {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
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
        LangChain4jToolBridge bridge = new LangChain4jToolBridge(
                List.of(tool), new AgentToolMetrics(registry));
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
        assertThat(registry.find(AgentToolMetrics.TOOL_DURATION)
                .tags("tool", "searchKnowledge", "outcome", "success")
                .timer())
                .isNotNull()
                .extracting(timer -> timer.count())
                .isEqualTo(2L);
    }

    @Test
    void recordsToolFailuresEvenWhenBridgeReturnsFriendlyErrorText() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        ToolDefinition tool = new ToolDefinition() {
            @Override public String name() { return "searchKnowledge"; }
            @Override public String description() { return "test"; }
            @Override public Map<String, Object> inputSchema() { return Map.of(); }
            @Override
            public String execute(Map<String, Object> arguments, AgentExecutionContext context) {
                throw new IllegalStateException("search unavailable");
            }
        };
        LangChain4jToolBridge bridge = new LangChain4jToolBridge(
                List.of(tool), new AgentToolMetrics(registry));
        AgentExecutionContext context = context("failed", 101L);
        AgentRunTrace trace = new AgentRunTrace(context);

        String result = bridge.searchKnowledge("pointer", 3, parameters(context, trace));

        assertThat(result).contains("工具执行出错");
        assertThat(trace.toolCalls()).singleElement().satisfies(call -> {
            assertThat(call.success()).isFalse();
            assertThat(call.failureType()).isEqualTo("IllegalStateException");
        });
        assertThat(registry.find(AgentToolMetrics.TOOL_DURATION)
                .tags("tool", "searchKnowledge", "outcome", "error")
                .timer())
                .isNotNull()
                .extracting(timer -> timer.count())
                .isEqualTo(1L);
    }

    private InvocationParameters parameters(AgentExecutionContext context, AgentRunTrace trace) {
        return AgentInvocationParameters.create(context, trace);
    }

    private AgentExecutionContext context(String sessionId, Long userId) {
        return new AgentExecutionContext(
                sessionId, userId, 1L, Set.of(1L), AgentChannel.WEB, "trace-" + sessionId);
    }
}
