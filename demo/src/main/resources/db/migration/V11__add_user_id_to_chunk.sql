-- V11: document_chunk 加 user_id，实现私人知识库隔离
ALTER TABLE document_chunk ADD COLUMN IF NOT EXISTS user_id BIGINT DEFAULT NULL;
COMMENT ON COLUMN document_chunk.user_id IS '上传者ID，NULL=未关联用户；user_id + kb_id IS NULL = 私人文档';

CREATE INDEX IF NOT EXISTS idx_chunk_user ON document_chunk(user_id);
CREATE INDEX IF NOT EXISTS idx_chunk_user_kb ON document_chunk(user_id, kb_id);

-- 回填存量数据：通过 doc_id 关联 document 表取 user_id 和 kb_id
UPDATE document_chunk dc
SET user_id = d.user_id,
    kb_id = d.kb_id
FROM document d
WHERE dc.doc_id = d.doc_id
  AND dc.user_id IS NULL;
