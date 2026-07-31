package com.firedemo.edumind.assistant.context;

import com.firedemo.edumind.platform.web.RequestIdFilter;
import com.firedemo.edumind.knowledge.SharedKbMemberMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;

import java.util.HashSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class AgentExecutionContextTest {

    @AfterEach
    void clearMdc() {
        MDC.remove(RequestIdFilter.MDC_REQUEST_ID);
    }

    @Test
    void defensivelyCopiesAuthorizationScopes() {
        Set<Long> mutableKbIds = new HashSet<>(Set.of(1L));

        AgentExecutionContext context = new AgentExecutionContext(
                " session-1 ", 10L, 20L, mutableKbIds, AgentChannel.WEB, " trace-1 ");
        mutableKbIds.add(2L);

        assertThat(context.sessionId()).isEqualTo("session-1");
        assertThat(context.traceId()).isEqualTo("trace-1");
        assertThat(context.accessibleKbIds()).containsExactly(1L);
        assertThatThrownBy(() -> context.accessibleKbIds().add(3L))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void rejectsMissingLogicalRunIdentifiers() {
        assertThatThrownBy(() -> new AgentExecutionContext(
                " ", 10L, null, Set.of(), AgentChannel.WEB, "trace"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("sessionId is required");
        assertThatThrownBy(() -> new AgentExecutionContext(
                "session", 10L, null, Set.of(), null, "trace"))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("channel is required");
        assertThatThrownBy(() -> new AgentExecutionContext(
                "session", 10L, null, Set.of(), AgentChannel.WEB, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("traceId is required");
    }

    @Test
    void anonymousContextFailsClosedWhenAUserIdIsRequired() {
        AgentExecutionContext context = new AgentExecutionContext(
                "session", null, null, Set.of(), AgentChannel.INTERNAL, "trace");

        assertThat(context.isAuthenticated()).isFalse();
        assertThatThrownBy(context::requireUserId)
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Authenticated user context is required");
    }

    @Test
    void factoryLoadsAuthorizationScopesAndPropagatesRequestTrace() {
        SharedKbMemberMapper mapper = mock(SharedKbMemberMapper.class);
        when(mapper.selectKbIdsByUserId(10L)).thenReturn(Set.of(1L, 2L));
        MDC.put(RequestIdFilter.MDC_REQUEST_ID, "request-trace");
        AgentExecutionContextFactory factory = new AgentExecutionContextFactory(mapper);

        AgentExecutionContext context = factory.create(
                "session", 10L, 20L, AgentChannel.WEB);

        assertThat(context.userId()).isEqualTo(10L);
        assertThat(context.courseId()).isEqualTo(20L);
        assertThat(context.accessibleKbIds()).containsExactlyInAnyOrder(1L, 2L);
        assertThat(context.traceId()).isEqualTo("request-trace");
        verify(mapper).selectKbIdsByUserId(10L);
    }

    @Test
    void factoryDoesNotQueryPermissionsForAnonymousRuns() {
        SharedKbMemberMapper mapper = mock(SharedKbMemberMapper.class);
        AgentExecutionContextFactory factory = new AgentExecutionContextFactory(mapper);

        AgentExecutionContext context = factory.create(
                "session", null, null, AgentChannel.INTERNAL);

        assertThat(context.accessibleKbIds()).isEmpty();
        assertThat(context.traceId()).hasSize(32);
        verifyNoInteractions(mapper);
    }
}
