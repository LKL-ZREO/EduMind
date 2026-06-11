-- 目录节点表（知识库目录树）
CREATE TABLE IF NOT EXISTS directory_node (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL,
    parent_id BIGINT DEFAULT NULL,
    label VARCHAR(255) NOT NULL,
    node_type VARCHAR(20) NOT NULL DEFAULT 'folder',
    doc_id VARCHAR(64) DEFAULT NULL,
    sort_order INT DEFAULT 0,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

COMMENT ON TABLE directory_node IS '目录节点（知识库目录树）';
COMMENT ON COLUMN directory_node.user_id IS '所属用户ID';
COMMENT ON COLUMN directory_node.parent_id IS '父节点ID，null=根节点';
COMMENT ON COLUMN directory_node.label IS '显示名称';
COMMENT ON COLUMN directory_node.node_type IS '节点类型：folder/file';
COMMENT ON COLUMN directory_node.doc_id IS '关联文档ID（file类型）';
COMMENT ON COLUMN directory_node.sort_order IS '同级排序序号';

CREATE INDEX IF NOT EXISTS idx_dir_node_user ON directory_node(user_id);
CREATE INDEX IF NOT EXISTS idx_dir_node_parent ON directory_node(parent_id);

-- 更新触发器
DROP TRIGGER IF EXISTS update_directory_node_updated_at ON directory_node;
CREATE TRIGGER update_directory_node_updated_at BEFORE UPDATE ON directory_node
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();
