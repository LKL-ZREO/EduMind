package com.firedemo.edumind.assistant.tool;

import com.firedemo.edumind.assistant.context.AgentChannel;
import com.firedemo.edumind.assistant.context.AgentExecutionContext;
import com.firedemo.edumind.knowledge.retrieval.RagResult;
import com.firedemo.edumind.knowledge.retrieval.RagSearchRequest;
import com.firedemo.edumind.knowledge.retrieval.RagService;
import org.junit.jupiter.api.Test;

import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ConcurrentLinkedQueue;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class ToolContextConcurrencyTest {

    @Test
    void isolatesExplicitContextsAcrossConcurrentAsyncBoundaries() throws Exception {
        AgentExecutionContext first = context("first", 101L, 1L, 11L);
        AgentExecutionContext second = context("second", 202L, 2L, 22L);
        CyclicBarrier barrier = new CyclicBarrier(2);

        try (ExecutorService executor = Executors.newFixedThreadPool(2)) {
            Future<AgentExecutionContext> firstResult = executor.submit(
                    () -> captureAfterBarrier(first, barrier));
            Future<AgentExecutionContext> secondResult = executor.submit(
                    () -> captureAfterBarrier(second, barrier));

            assertThat(firstResult.get()).isSameAs(first);
            assertThat(secondResult.get()).isSameAs(second);
        }
    }

    @Test
    void explicitContextSurvivesAnUnmanagedAsyncBoundary() {
        AgentExecutionContext context = context("async", 101L, 1L, 11L);

        AgentExecutionContext asyncContext = CompletableFuture.supplyAsync(() -> context).join();

        assertThat(asyncContext).isSameAs(context);
    }

    @Test
    void keepsRagAuthorizationScopesSeparateDuringConcurrentToolCalls() throws Exception {
        RagService ragService = mock(RagService.class);
        ConcurrentLinkedQueue<RagSearchRequest> requests = new ConcurrentLinkedQueue<>();
        when(ragService.search(any())).thenAnswer(invocation -> {
            requests.add(invocation.getArgument(0));
            return RagResult.builder().formattedContent("ok").build();
        });
        KnowledgeSearchTool tool = new KnowledgeSearchTool(ragService);
        CyclicBarrier barrier = new CyclicBarrier(2);

        try (ExecutorService executor = Executors.newFixedThreadPool(2)) {
            Future<?> first = executor.submit(() -> executeSearch(
                    tool, context("first", 101L, 1L, 11L), barrier));
            Future<?> second = executor.submit(() -> executeSearch(
                    tool, context("second", 202L, 2L, 22L), barrier));
            first.get();
            second.get();
        }

        assertThat(requests).hasSize(2);
        assertThat(requests).anySatisfy(request -> {
            assertThat(request.getUserId()).isEqualTo(101L);
            assertThat(request.getAccessibleKbIds()).containsExactly(1L);
            assertThat(request.getCourseId()).isEqualTo(11L);
        });
        assertThat(requests).anySatisfy(request -> {
            assertThat(request.getUserId()).isEqualTo(202L);
            assertThat(request.getAccessibleKbIds()).containsExactly(2L);
            assertThat(request.getCourseId()).isEqualTo(22L);
        });
    }

    @Test
    void anonymousContextFailsClosedWithoutCallingRag() {
        RagService ragService = mock(RagService.class);
        KnowledgeSearchTool tool = new KnowledgeSearchTool(ragService);
        AgentExecutionContext anonymous = new AgentExecutionContext(
                "anonymous", null, null, Set.of(), AgentChannel.MCP, "trace-anonymous");

        String result = tool.execute(java.util.Map.of("query", "pointer"), anonymous);

        assertThat(result).contains("未认证");
        verifyNoInteractions(ragService);
    }

    private AgentExecutionContext captureAfterBarrier(AgentExecutionContext context,
                                                       CyclicBarrier barrier) throws Exception {
        barrier.await();
        return context;
    }

    private void executeSearch(KnowledgeSearchTool tool,
                               AgentExecutionContext context,
                               CyclicBarrier barrier) {
        try {
            barrier.await();
            tool.execute(java.util.Map.of("query", "pointer"), context);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private AgentExecutionContext context(String sessionId,
                                          Long userId,
                                          Long kbId,
                                          Long courseId) {
        return new AgentExecutionContext(
                sessionId, userId, courseId, Set.of(kbId), AgentChannel.WEB, "trace-" + sessionId);
    }
}
