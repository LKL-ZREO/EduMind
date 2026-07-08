-- V6__add_chunk_metadata.sql
-- 为文档块表添加元数据字段

-- 添加 metadata 字段（JSON格式存储）
ALTER TABLE document_chunk ADD COLUMN IF NOT EXISTS metadata JSONB;

COMMENT ON COLUMN document_chunk.metadata IS '文档块元数据，JSON格式存储';

-- 创建索引加速查询
CREATE INDEX IF NOT EXISTS idx_document_chunk_doc_id ON document_chunk(doc_id);
CREATE INDEX IF NOT EXISTS idx_document_chunk_metadata_class_id ON document_chunk((metadata->>'classId'));
CREATE INDEX IF NOT EXISTS idx_document_chunk_metadata_type ON document_chunk((metadata->>'type'));
