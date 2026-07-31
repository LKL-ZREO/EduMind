package com.firedemo.edumind.assistant.context;

import org.junit.jupiter.api.Test;

import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AgentRunTraceTest {

    @Test
    void recordsSuccessfulAndFailedCallsWithoutArgumentsOrErrorMessages() {
        AgentRunTrace trace = new AgentRunTrace(context());

        String result = trace.traceToolCall("searchKnowledge", () -> "result");
        assertThatThrownBy(() -> trace.traceToolCall(
                "queryStudentStats", () -> { throw new IllegalStateException("sensitive detail"); }))
                .isInstanceOf(IllegalStateException.class);

        assertThat(result).isEqualTo("result");
        assertThat(trace.traceId()).isEqualTo("trace-1");
        assertThat(trace.hasToolCalls()).isTrue();
        assertThat(trace.toolCalls()).hasSize(2);
        assertThat(trace.toolCalls().get(0).success()).isTrue();
        assertThat(trace.toolCalls().get(1).success()).isFalse();
        assertThat(trace.toolCalls().get(1).failureType()).isEqualTo("IllegalStateException");
        assertThat(trace.toolCalls().toString()).doesNotContain("sensitive detail");
    }

    @Test
    void recordsConcurrentToolCallsInOneRequestTrace() throws Exception {
        AgentRunTrace trace = new AgentRunTrace(context());

        try (ExecutorService executor = Executors.newFixedThreadPool(4)) {
            @SuppressWarnings("unchecked")
            Future<String>[] calls = new Future[40];
            for (int index = 0; index < calls.length; index++) {
                int callIndex = index;
                calls[index] = executor.submit(() -> trace.traceToolCall(
                        "tool-" + callIndex, () -> "ok"));
            }
            for (Future<String> call : calls) {
                assertThat(call.get()).isEqualTo("ok");
            }
        }

        assertThat(trace.toolCallCount()).isEqualTo(40);
        assertThat(trace.toolCalls()).allSatisfy(call -> assertThat(call.success()).isTrue());
        assertThat(trace.elapsedMillis()).isNotNegative();
    }

    private AgentExecutionContext context() {
        return new AgentExecutionContext(
                "session-1", 10L, 20L, Set.of(1L), AgentChannel.WEB, "trace-1");
    }
}
