package com.firedemo.demo.eval.persistence;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.firedemo.demo.eval.model.EvalCaseResult;
import com.firedemo.demo.eval.model.EvalRunConfig;
import com.firedemo.demo.eval.model.EvalRunDetails;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

@Component
public class EvalRunStore {

    private static final int MAX_FAILURE_CHARS = 2_000;

    private final EvalRunMapper runMapper;
    private final EvalCaseResultMapper caseMapper;
    private final ObjectMapper objectMapper;

    public EvalRunStore(EvalRunMapper runMapper, EvalCaseResultMapper caseMapper,
                        ObjectMapper objectMapper) {
        this.runMapper = runMapper;
        this.caseMapper = caseMapper;
        this.objectMapper = objectMapper;
    }

    public Long start(EvalRunConfig config) {
        EvalRunEntity run = new EvalRunEntity();
        run.setStatus("RUNNING");
        run.setDatasetVersion(config.datasetVersion());
        run.setDatasetHash(config.datasetHash());
        run.setGitCommit(config.gitCommit());
        run.setConfigJson(writeJson(config));
        runMapper.insert(run);
        return run.getId();
    }

    public void saveCase(Long runId, EvalCaseResult result) {
        EvalCaseResultEntity entity = new EvalCaseResultEntity();
        entity.setRunId(runId);
        entity.setCaseId(result.caseId());
        entity.setQueryText(result.query());
        entity.setSourceDocId(result.sourceDocId());
        entity.setReferenceAnswer(result.referenceAnswer());
        entity.setGeneratedAnswer(result.generatedAnswer());
        entity.setRetrievedChunkIdsJson(writeJson(result.retrievedChunkIds()));
        entity.setKeywordRecall(optionalMetric(result.keywordRecall()));
        entity.setContentCoverage(optionalMetric(result.contentCoverage()));
        entity.setReciprocalRank(result.reciprocalRank());
        entity.setNdcg(result.ndcg());
        entity.setRetrievalHit(result.retrievalHit());
        entity.setFaithfulness(result.faithfulness());
        entity.setAnswerRelevancy(result.answerRelevancy());
        entity.setRetrievalMs(result.retrievalMs());
        entity.setGenerationMs(result.generationMs());
        entity.setErrorMessage(result.error());
        caseMapper.insert(entity);
    }

    public void complete(Long runId, Map<String, Double> summary, int numCases,
                         long durationMs, boolean qualityGatePassed) {
        runMapper.complete(runId, writeJson(summary), numCases, durationMs, qualityGatePassed);
    }

    public void fail(Long runId, Throwable error) {
        String message = error == null || error.getMessage() == null ? "unknown" : error.getMessage();
        runMapper.fail(runId, message.length() <= MAX_FAILURE_CHARS
                ? message : message.substring(0, MAX_FAILURE_CHARS));
    }

    public List<EvalRunEntity> listRecent(int limit) {
        return runMapper.listRecent(Math.max(1, Math.min(limit, 100)));
    }

    public EvalRunDetails getDetails(Long runId) {
        EvalRunEntity run = runMapper.findById(runId);
        return run == null ? null : new EvalRunDetails(run, caseMapper.findByRunId(runId));
    }

    private Double optionalMetric(double value) {
        return value < 0 ? null : value;
    }

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Unable to serialize evaluation evidence", e);
        }
    }
}
