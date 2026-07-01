-- ============================================================
-- V17: pgvector 向量索引 + pg_trgm 全文检索索引
-- ============================================================
-- 前提：DBA 已手动执行 CREATE EXTENSION IF NOT EXISTS vector;
--       若 pgvector 未安装，embedding_vec 相关语句会被跳过
-- ============================================================

-- ① pg_trgm 扩展（PostgreSQL 内置，三元组模糊匹配）
CREATE EXTENSION IF NOT EXISTS pg_trgm;

-- ② embedding_vec 列（仅当 pgvector 已安装时添加）
DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM pg_extension WHERE extname = 'vector') THEN
        ALTER TABLE document_chunk ADD COLUMN IF NOT EXISTS embedding_vec vector(512);
    END IF;
END $$;

-- ③ IVFFlat 向量索引（仅当列存在时创建）
DO $$
BEGIN
    IF EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_name = 'document_chunk' AND column_name = 'embedding_vec'
    ) THEN
        -- 需要先有数据才能建 IVFFlat 索引，create index if not exists 处理
        EXECUTE '
            CREATE INDEX IF NOT EXISTS idx_chunk_embedding
            ON document_chunk USING ivfflat (embedding_vec vector_cosine_ops)
            WITH (lists = 100)
        ';
    END IF;
END $$;

-- ④ 全文检索 GIN 索引（pg_trgm，即使无 pgvector 也能用）
DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM pg_extension WHERE extname = 'pg_trgm') THEN
        EXECUTE '
            CREATE INDEX IF NOT EXISTS idx_chunk_content_trgm
            ON document_chunk USING gin (content gin_trgm_ops)
        ';
    END IF;
END $$;
