package com.firedemo.edumind.knowledge;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.UUID;

/** 短生命周期重分类任务状态注册表。 */
@Component
public class KnowledgeReclassificationTaskRegistry {

    private final Cache<String, KnowledgeReclassificationTaskDTO> tasks = Caffeine.newBuilder()
            .expireAfterWrite(Duration.ofHours(2))
            .maximumSize(2_000)
            .build();

    public synchronized KnowledgeReclassificationTaskDTO create(Long classId) {
        KnowledgeReclassificationTaskDTO active = findActive(classId);
        if (active != null) return active;

        KnowledgeReclassificationTaskDTO task = KnowledgeReclassificationTaskDTO.builder()
                .taskId(UUID.randomUUID().toString().replace("-", ""))
                .classId(classId)
                .status("PENDING")
                .total(0)
                .processed(0)
                .reclassified(0)
                .remainingOther(0)
                .failed(0)
                .build();
        tasks.put(task.getTaskId(), task);
        return snapshot(task);
    }

    public KnowledgeReclassificationTaskDTO get(String taskId) {
        KnowledgeReclassificationTaskDTO task = tasks.getIfPresent(taskId);
        return task == null ? null : snapshot(task);
    }

    public void markRunning(String taskId, int total) {
        mutate(taskId, task -> {
            task.setStatus("RUNNING");
            task.setTotal(total);
            task.setStartedAt(LocalDateTime.now());
        });
    }

    public void advance(String taskId, int processed, int reclassified, int failed) {
        mutate(taskId, task -> {
            task.setProcessed(task.getProcessed() + processed);
            task.setReclassified(task.getReclassified() + reclassified);
            task.setFailed(task.getFailed() + failed);
        });
    }

    public void complete(String taskId, int remainingOther) {
        mutate(taskId, task -> {
            task.setRemainingOther(remainingOther);
            task.setStatus(task.getFailed() > 0 ? "COMPLETED_WITH_ERRORS" : "COMPLETED");
            task.setCompletedAt(LocalDateTime.now());
        });
    }

    public void fail(String taskId, String errorMessage, int remainingOther) {
        mutate(taskId, task -> {
            task.setStatus("FAILED");
            task.setErrorMessage(errorMessage);
            task.setRemainingOther(remainingOther);
            task.setCompletedAt(LocalDateTime.now());
        });
    }

    private KnowledgeReclassificationTaskDTO findActive(Long classId) {
        return tasks.asMap().values().stream()
                .filter(task -> classId.equals(task.getClassId()))
                .filter(task -> "PENDING".equals(task.getStatus()) || "RUNNING".equals(task.getStatus()))
                .findFirst()
                .map(this::snapshot)
                .orElse(null);
    }

    private void mutate(String taskId, java.util.function.Consumer<KnowledgeReclassificationTaskDTO> action) {
        KnowledgeReclassificationTaskDTO task = tasks.getIfPresent(taskId);
        if (task == null) return;
        synchronized (task) {
            action.accept(task);
        }
    }

    private KnowledgeReclassificationTaskDTO snapshot(KnowledgeReclassificationTaskDTO task) {
        synchronized (task) {
            return KnowledgeReclassificationTaskDTO.builder()
                    .taskId(task.getTaskId())
                    .classId(task.getClassId())
                    .status(task.getStatus())
                    .total(task.getTotal())
                    .processed(task.getProcessed())
                    .reclassified(task.getReclassified())
                    .remainingOther(task.getRemainingOther())
                    .failed(task.getFailed())
                    .errorMessage(task.getErrorMessage())
                    .startedAt(task.getStartedAt())
                    .completedAt(task.getCompletedAt())
                    .build();
        }
    }
}
