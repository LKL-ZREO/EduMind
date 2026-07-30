package com.firedemo.demo.DTO;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 教师知识点变更后，历史“其他”错误重分类任务的可观察状态。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class KnowledgeReclassificationTaskDTO {

    private String taskId;
    private Long classId;
    /** PENDING / RUNNING / COMPLETED / COMPLETED_WITH_ERRORS / FAILED */
    private String status;
    private Integer total;
    private Integer processed;
    private Integer reclassified;
    private Integer remainingOther;
    private Integer failed;
    private String errorMessage;
    private LocalDateTime startedAt;
    private LocalDateTime completedAt;
}
