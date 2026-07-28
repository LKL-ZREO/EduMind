package com.firedemo.demo.agent.langchain4j;

import com.firedemo.demo.agent.context.AgentExecutionContext;
import com.firedemo.demo.agent.context.AgentRunTrace;
import dev.langchain4j.invocation.InvocationParameters;

import java.util.Map;
import java.util.Objects;

/** Bridges application-owned run state through LangChain4j without model arguments. */
public final class AgentInvocationParameters {

    private static final String EXECUTION_CONTEXT = "edumind.executionContext";
    private static final String RUN_TRACE = "edumind.runTrace";

    public static InvocationParameters create(AgentExecutionContext context, AgentRunTrace trace) {
        return InvocationParameters.from(Map.of(
                EXECUTION_CONTEXT, Objects.requireNonNull(context, "context is required"),
                RUN_TRACE, Objects.requireNonNull(trace, "trace is required")));
    }

    public static AgentExecutionContext requireContext(InvocationParameters parameters) {
        return require(parameters, EXECUTION_CONTEXT, AgentExecutionContext.class);
    }

    public static AgentRunTrace requireTrace(InvocationParameters parameters) {
        return require(parameters, RUN_TRACE, AgentRunTrace.class);
    }

    private static <T> T require(InvocationParameters parameters, String key, Class<T> type) {
        Objects.requireNonNull(parameters, "invocation parameters are required");
        Object value = parameters.get(key);
        if (!type.isInstance(value)) {
            throw new IllegalStateException("Missing trusted invocation parameter: " + key);
        }
        return type.cast(value);
    }

    private AgentInvocationParameters() {
    }
}
