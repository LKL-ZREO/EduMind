package com.firedemo.edumind.assistant.evaluation.persistence;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface EvalCaseResultMapper {

    @Insert("""
            INSERT INTO rag_eval_case_result
                (run_id, case_id, query_text, source_doc_id, reference_answer,
                 generated_answer, retrieved_chunk_ids, keyword_recall, content_coverage,
                 reciprocal_rank, ndcg, retrieval_hit, faithfulness, answer_relevancy,
                 retrieval_ms, generation_ms, error_message)
            VALUES
                (#{runId}, #{caseId}, #{queryText}, #{sourceDocId}, #{referenceAnswer},
                 #{generatedAnswer}, CAST(#{retrievedChunkIdsJson} AS jsonb), #{keywordRecall},
                 #{contentCoverage}, #{reciprocalRank}, #{ndcg}, #{retrievalHit},
                 #{faithfulness}, #{answerRelevancy}, #{retrievalMs}, #{generationMs}, #{errorMessage})
            """)
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(EvalCaseResultEntity result);

    @Select("""
            SELECT id, run_id, case_id, query_text, source_doc_id, reference_answer,
                   generated_answer, retrieved_chunk_ids::text AS retrieved_chunk_ids_json,
                   keyword_recall, content_coverage, reciprocal_rank, ndcg, retrieval_hit,
                   faithfulness, answer_relevancy, retrieval_ms, generation_ms, error_message
            FROM rag_eval_case_result WHERE run_id = #{runId} ORDER BY case_id
            """)
    List<EvalCaseResultEntity> findByRunId(@Param("runId") Long runId);
}
