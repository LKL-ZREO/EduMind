-- 对话记录表
CREATE TABLE IF NOT EXISTS chat_history (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL,
    session_id VARCHAR(64) NOT NULL,
    role VARCHAR(20) NOT NULL,
    content TEXT NOT NULL,
    model VARCHAR(50),
    tokens_used INT DEFAULT 0,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

COMMENT ON TABLE chat_history IS '对话记录表';
COMMENT ON COLUMN chat_history.user_id IS '用户ID';
COMMENT ON COLUMN chat_history.session_id IS '会话ID';
COMMENT ON COLUMN chat_history.role IS '角色：user/assistant';
COMMENT ON COLUMN chat_history.content IS '消息内容';
COMMENT ON COLUMN chat_history.model IS '使用的模型';
COMMENT ON COLUMN chat_history.tokens_used IS '使用的token数';

-- 索引
CREATE INDEX IF NOT EXISTS idx_chat_history_user_id ON chat_history(user_id);
CREATE INDEX IF NOT EXISTS idx_chat_history_session_id ON chat_history(session_id);
CREATE INDEX IF NOT EXISTS idx_chat_history_created_at ON chat_history(created_at);

-- 文档表
CREATE TABLE IF NOT EXISTS document (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL,
    doc_id VARCHAR(64) NOT NULL UNIQUE,
    doc_name VARCHAR(255) NOT NULL,
    file_path VARCHAR(500),
    file_size BIGINT DEFAULT 0,
    content_type VARCHAR(100),
    status SMALLINT DEFAULT 1,
    chunk_count INT DEFAULT 0,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

COMMENT ON TABLE document IS '文档表';
COMMENT ON COLUMN document.user_id IS '上传用户ID';
COMMENT ON COLUMN document.doc_id IS '文档唯一标识';
COMMENT ON COLUMN document.doc_name IS '文档名称';
COMMENT ON COLUMN document.file_path IS '文件存储路径';
COMMENT ON COLUMN document.file_size IS '文件大小（字节）';
COMMENT ON COLUMN document.content_type IS '文件类型';
COMMENT ON COLUMN document.status IS '状态：0-处理中 1-已完成 2-失败';
COMMENT ON COLUMN document.chunk_count IS '切割块数';

-- 文档块表（RAG用）
-- 注意：如果需要向量检索，请先安装 pgvector 扩展: CREATE EXTENSION IF NOT EXISTS vector;
CREATE TABLE IF NOT EXISTS document_chunk (
    id BIGSERIAL PRIMARY KEY,
    doc_id VARCHAR(64) NOT NULL,
    chunk_index INT NOT NULL,
    content TEXT NOT NULL,
    token_count INT DEFAULT 0,
    char_count INT DEFAULT 0,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_doc_chunk UNIQUE (doc_id, chunk_index)
);

COMMENT ON TABLE document_chunk IS '文档块表';
COMMENT ON COLUMN document_chunk.doc_id IS '文档ID';
COMMENT ON COLUMN document_chunk.chunk_index IS '块序号';
COMMENT ON COLUMN document_chunk.content IS '块内容';
COMMENT ON COLUMN document_chunk.token_count IS 'token数';
COMMENT ON COLUMN document_chunk.char_count IS '字符数';

CREATE INDEX IF NOT EXISTS idx_document_chunk_doc_id ON document_chunk(doc_id);

-- 如果需要向量字段，取消下面注释（需先安装 pgvector）
-- ALTER TABLE document_chunk ADD COLUMN IF NOT EXISTS embedding VECTOR(384);
-- CREATE INDEX IF NOT EXISTS idx_document_chunk_embedding ON document_chunk USING ivfflat (embedding vector_cosine_ops);

-- 更新触发器
CREATE OR REPLACE FUNCTION update_updated_at_column()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = CURRENT_TIMESTAMP;
    RETURN NEW;
END;
$$ language 'plpgsql';

DROP TRIGGER IF EXISTS update_chat_history_updated_at ON chat_history;
CREATE TRIGGER update_chat_history_updated_at BEFORE UPDATE ON chat_history
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

DROP TRIGGER IF EXISTS update_document_updated_at ON document;
CREATE TRIGGER update_document_updated_at BEFORE UPDATE ON document
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();
