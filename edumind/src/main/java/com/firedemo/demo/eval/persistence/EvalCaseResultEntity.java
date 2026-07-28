package com.firedemo.demo.eval.persistence;

import lombok.Data;

@Data
public class EvalCaseResultEntity {
    private Long id;
    private Long runId;
    private int caseId;
    private String queryText;
    private String sourceDocId;
    private String referenceAnswer;
    private String generatedAnswer;
    private String retrievedChunkIdsJson;
    private Double keywordRecall;
    private Double contentCoverage;
    private double reciprocalRank;
    private double ndcg;
    private boolean retrievalHit;
    private Boolean faithfulness;
    private Boolean answerRelevancy;
    private long retrievalMs;
    private long generationMs;
    private String errorMessage;
}
