CREATE TABLE IF NOT EXISTS rag_eval_run (
    id BIGSERIAL PRIMARY KEY,
    status VARCHAR(20) NOT NULL,
    dataset_version VARCHAR(120) NOT NULL,
    dataset_hash VARCHAR(64) NOT NULL,
    git_commit VARCHAR(80) NOT NULL,
    config_json JSONB NOT NULL,
    summary_json JSONB,
    num_cases INTEGER NOT NULL DEFAULT 0,
    duration_ms BIGINT,
    quality_gate_passed BOOLEAN,
    failure_message TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    completed_at TIMESTAMP
);

CREATE TABLE IF NOT EXISTS rag_eval_case_result (
    id BIGSERIAL PRIMARY KEY,
    run_id BIGINT NOT NULL REFERENCES rag_eval_run(id) ON DELETE CASCADE,
    case_id INTEGER NOT NULL,
    query_text TEXT NOT NULL,
    source_doc_id VARCHAR(255),
    reference_answer TEXT,
    generated_answer TEXT,
    retrieved_chunk_ids JSONB NOT NULL DEFAULT '[]'::jsonb,
    keyword_recall DOUBLE PRECISION,
    content_coverage DOUBLE PRECISION,
    reciprocal_rank DOUBLE PRECISION NOT NULL,
    ndcg DOUBLE PRECISION NOT NULL,
    retrieval_hit BOOLEAN NOT NULL,
    faithfulness BOOLEAN,
    answer_relevancy BOOLEAN,
    retrieval_ms BIGINT NOT NULL,
    generation_ms BIGINT NOT NULL,
    error_message TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    UNIQUE (run_id, case_id)
);

CREATE INDEX IF NOT EXISTS idx_rag_eval_run_created_at
    ON rag_eval_run(created_at DESC);
CREATE INDEX IF NOT EXISTS idx_rag_eval_case_run_id
    ON rag_eval_case_result(run_id);
