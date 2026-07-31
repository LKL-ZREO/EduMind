package com.firedemo.edumind.assistant.context;

import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Sinks;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class AgentUiEventBus {

    private final Map<String, Sinks.Many<AgentUiEvent>> channels = new ConcurrentHashMap<>();

    public Flux<AgentUiEvent> open(String traceId) {
        return channel(traceId).asFlux();
    }

    public void publish(String traceId, String type, Map<String, Object> payload) {
        if (traceId == null || traceId.isBlank()) return;
        channel(traceId).tryEmitNext(new AgentUiEvent(type, payload));
    }

    public void complete(String traceId) {
        Sinks.Many<AgentUiEvent> sink = channels.remove(traceId);
        if (sink != null) sink.tryEmitComplete();
    }

    private Sinks.Many<AgentUiEvent> channel(String traceId) {
        return channels.computeIfAbsent(traceId,
                ignored -> Sinks.many().replay().limit(64));
    }
}
