package com.firedemo.demo.agent.context;

import com.firedemo.demo.common.web.RequestIdFilter;
import com.firedemo.demo.mapper.SharedKbMemberMapper;
import lombok.RequiredArgsConstructor;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;

import java.util.Set;
import java.util.UUID;

/** Builds trusted execution contexts at application boundaries. */
@Component
@RequiredArgsConstructor
public class AgentExecutionContextFactory {

    private final SharedKbMemberMapper sharedKbMemberMapper;

    public AgentExecutionContext create(String sessionId,
                                        Long userId,
                                        Long courseId,
                                        AgentChannel channel) {
        Set<Long> kbIds = userId == null
                ? Set.of()
                : nullToEmpty(sharedKbMemberMapper.selectKbIdsByUserId(userId));
        return create(sessionId, userId, courseId, kbIds, channel);
    }

    public AgentExecutionContext create(String sessionId,
                                        Long userId,
                                        Long courseId,
                                        Set<Long> accessibleKbIds,
                                        AgentChannel channel) {
        return new AgentExecutionContext(
                sessionId,
                userId,
                courseId,
                accessibleKbIds,
                channel,
                currentTraceId());
    }

    private Set<Long> nullToEmpty(Set<Long> values) {
        return values == null ? Set.of() : values;
    }

    private String currentTraceId() {
        String requestId = MDC.get(RequestIdFilter.MDC_REQUEST_ID);
        return requestId == null || requestId.isBlank()
                ? UUID.randomUUID().toString().replace("-", "")
                : requestId;
    }
}
