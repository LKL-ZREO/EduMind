package com.firedemo.demo.agent.memory;

import com.firedemo.demo.agent.context.AgentChannel;
import com.firedemo.demo.agent.context.AgentExecutionContext;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AgentMemoryIdTest {

    @Test
    void scopesTheSameSessionIdByUser() {
        AgentMemoryId firstUser = new AgentMemoryId(10L, "shared-session");
        AgentMemoryId secondUser = new AgentMemoryId(20L, "shared-session");

        assertThat(firstUser.storageKey()).isNotEqualTo(secondUser.storageKey());
        assertThat(firstUser.toString())
                .doesNotContain("shared-session")
                .doesNotContain("10");
    }

    @Test
    void buildsTheTrustedIdentityFromExecutionContext() {
        AgentExecutionContext context = new AgentExecutionContext(
                " session-1 ", 42L, 7L, Set.of(3L), AgentChannel.WEB, "trace-1");

        assertThat(AgentMemoryId.from(context))
                .isEqualTo(new AgentMemoryId(42L, "session-1"));
    }

    @Test
    void rejectsAnEmptySessionId() {
        assertThatThrownBy(() -> new AgentMemoryId(42L, " "))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("sessionId");
    }
}
