package com.firedemo.edumind.assistant.evaluation.persistence;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

@Mapper
public interface EvalRunMapper {

    @Insert("""
            INSERT INTO rag_eval_run
                (status, dataset_version, dataset_hash, git_commit, config_json)
            VALUES
                (#{status}, #{datasetVersion}, #{datasetHash}, #{gitCommit}, CAST(#{configJson} AS jsonb))
            """)
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(EvalRunEntity run);

    @Update("""
            UPDATE rag_eval_run
            SET status = 'COMPLETED', summary_json = CAST(#{summaryJson} AS jsonb),
                num_cases = #{numCases}, duration_ms = #{durationMs},
                quality_gate_passed = #{qualityGatePassed}, completed_at = NOW()
            WHERE id = #{id}
            """)
    int complete(@Param("id") Long id,
                 @Param("summaryJson") String summaryJson,
                 @Param("numCases") int numCases,
                 @Param("durationMs") long durationMs,
                 @Param("qualityGatePassed") boolean qualityGatePassed);

    @Update("""
            UPDATE rag_eval_run
            SET status = 'FAILED', failure_message = #{message}, completed_at = NOW()
            WHERE id = #{id}
            """)
    int fail(@Param("id") Long id, @Param("message") String message);

    @Select("""
            SELECT id, status, dataset_version, dataset_hash, git_commit,
                   config_json::text AS config_json, summary_json::text AS summary_json,
                   num_cases, duration_ms, quality_gate_passed, failure_message,
                   created_at, completed_at
            FROM rag_eval_run ORDER BY id DESC LIMIT #{limit}
            """)
    List<EvalRunEntity> listRecent(@Param("limit") int limit);

    @Select("""
            SELECT id, status, dataset_version, dataset_hash, git_commit,
                   config_json::text AS config_json, summary_json::text AS summary_json,
                   num_cases, duration_ms, quality_gate_passed, failure_message,
                   created_at, completed_at
            FROM rag_eval_run WHERE id = #{id}
            """)
    EvalRunEntity findById(@Param("id") Long id);
}
