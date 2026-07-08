-- V4__add_document_chunk_embedding.sql
-- 为文档块表添加向量存储字段

-- 添加 embedding 字段（以文本形式存储，逗号分隔的浮点数）
ALTER TABLE document_chunk ADD COLUMN IF NOT EXISTS embedding TEXT;

-- 添加其他RAG相关字段
ALTER TABLE document_chunk ADD COLUMN IF NOT EXISTS prev_summary TEXT;
ALTER TABLE document_chunk ADD COLUMN IF NOT EXISTS next_summary TEXT;
ALTER TABLE document_chunk ADD COLUMN IF NOT EXISTS sub_index INT DEFAULT 0;

-- 添加文档名称字段（方便查询时显示）
ALTER TABLE document_chunk ADD COLUMN IF NOT EXISTS doc_name VARCHAR(255);

COMMENT ON COLUMN document_chunk.embedding IS '文本嵌入向量，逗号分隔的浮点数';
COMMENT ON COLUMN document_chunk.prev_summary IS '前一块摘要';
COMMENT ON COLUMN document_chunk.next_summary IS '后一块摘要';
COMMENT ON COLUMN document_chunk.sub_index IS '子块序号（用于大块的进一步分割）';
COMMENT ON COLUMN document_chunk.doc_name IS '文档名称';
