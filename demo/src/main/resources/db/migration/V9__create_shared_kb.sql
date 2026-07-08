-- 共享知识库
CREATE TABLE IF NOT EXISTS shared_kb (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(100) NOT NULL,
    description VARCHAR(500) DEFAULT '',
    owner_id BIGINT NOT NULL,
    invite_token VARCHAR(64) UNIQUE,
    invite_expires_at TIMESTAMP,
    invite_max_uses INT DEFAULT 0,
    invite_use_count INT DEFAULT 0,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

COMMENT ON TABLE shared_kb IS '共享知识库';
COMMENT ON COLUMN shared_kb.owner_id IS '创建者ID';
COMMENT ON COLUMN shared_kb.invite_token IS '邀请链接token';
COMMENT ON COLUMN shared_kb.invite_expires_at IS '邀请链接过期时间';

-- 知识库成员
CREATE TABLE IF NOT EXISTS shared_kb_member (
    id BIGSERIAL PRIMARY KEY,
    kb_id BIGINT NOT NULL REFERENCES shared_kb(id) ON DELETE CASCADE,
    user_id BIGINT NOT NULL,
    role VARCHAR(20) NOT NULL DEFAULT 'member',
    joined_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE(kb_id, user_id)
);

COMMENT ON TABLE shared_kb_member IS '知识库成员';
COMMENT ON COLUMN shared_kb_member.role IS 'owner | admin | member';

-- document 加 kb_id
ALTER TABLE document ADD COLUMN IF NOT EXISTS kb_id BIGINT DEFAULT NULL;
COMMENT ON COLUMN document.kb_id IS '所属共享知识库ID，null=个人文档';

-- directory_node 加 kb_id（替代 is_shared）
ALTER TABLE directory_node ADD COLUMN IF NOT EXISTS kb_id BIGINT DEFAULT NULL;
COMMENT ON COLUMN directory_node.kb_id IS '所属共享知识库ID，null=个人目录';

-- document_chunk 加 kb_id（RAG 隔离用）
ALTER TABLE document_chunk ADD COLUMN IF NOT EXISTS kb_id BIGINT DEFAULT NULL;
COMMENT ON COLUMN document_chunk.kb_id IS '所属知识库ID，null=个人';

-- 索引
CREATE INDEX IF NOT EXISTS idx_shared_kb_owner ON shared_kb(owner_id);
CREATE INDEX IF NOT EXISTS idx_shared_kb_invite ON shared_kb(invite_token);
CREATE INDEX IF NOT EXISTS idx_skb_member_kb ON shared_kb_member(kb_id);
CREATE INDEX IF NOT EXISTS idx_skb_member_user ON shared_kb_member(user_id);
CREATE INDEX IF NOT EXISTS idx_doc_kb ON document(kb_id);
CREATE INDEX IF NOT EXISTS idx_dir_node_kb ON directory_node(kb_id);
CREATE INDEX IF NOT EXISTS idx_chunk_kb ON document_chunk(kb_id);

-- 触发器
DROP TRIGGER IF EXISTS update_shared_kb_updated_at ON shared_kb;
CREATE TRIGGER update_shared_kb_updated_at BEFORE UPDATE ON shared_kb
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();
