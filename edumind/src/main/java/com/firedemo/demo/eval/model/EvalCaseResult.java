package com.firedemo.demo.eval.model;

import lombok.Builder;

import java.util.List;

/** Per-case evidence retained for diagnosis and experiment comparison. */
@Builder
public record EvalCaseResult(
        int caseId,
        String query,
        String sourceDocId,
        String referenceAnswer,
        String generatedAnswer,
        List<String> retrievedChunkIds,
        double keywordRecall,
        double contentCoverage,
        double reciprocalRank,
        double ndcg,
        boolean retrievalHit,
        Boolean faithfulness,
        Boolean answerRelevancy,
        long retrievalMs,
        long generationMs,
        String error
) {
    public EvalCaseResult {
        retrievedChunkIds = retrievedChunkIds == null ? List.of() : List.copyOf(retrievedChunkIds);
    }
}
