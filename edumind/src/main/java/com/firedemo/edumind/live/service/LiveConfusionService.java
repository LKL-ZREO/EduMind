package com.firedemo.edumind.live.service;

import com.firedemo.edumind.live.LiveConfusionEvent;
import com.firedemo.edumind.live.LiveConfusionEventMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class LiveConfusionService {

    private final LiveConfusionEventMapper mapper;

    public LiveConfusionEvent findExisting(Long interactionId, String studentId) {
        return mapper.findByInteractionAndStudent(interactionId, studentId);
    }

    public void record(LiveConfusionEvent event) {
        mapper.insert(event);
    }

    public SessionSummary summarizeSession(Long sessionId) {
        List<Map<String, Object>> stats = mapper.countByKnowledgePoint(sessionId);
        List<LiveConfusionEvent> events = mapper.findBySessionId(sessionId);
        return new SessionSummary(stats, events);
    }

    public record SessionSummary(
            List<Map<String, Object>> stats,
            List<LiveConfusionEvent> events) {
    }
}
