package com.firedemo.edumind.assistant.evaluation.persistence;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class EvalRunEntity {
    private Long id;
    private String status;
    private String datasetVersion;
    private String datasetHash;
    private String gitCommit;
    private String configJson;
    private String summaryJson;
    private int numCases;
    private Long durationMs;
    private Boolean qualityGatePassed;
    private String failureMessage;
    private LocalDateTime createdAt;
    private LocalDateTime completedAt;
}
