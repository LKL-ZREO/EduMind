-- 清理仪表盘数据产生的知识库污染 chunk
-- 这些数据应通过 MCP 工具直接从业务表查询，不应混入 RAG 向量索引

DELETE FROM document_chunk WHERE doc_id LIKE 'dashboard_%';
