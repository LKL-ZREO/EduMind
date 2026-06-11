-- 目录节点增加共享标记
ALTER TABLE directory_node ADD COLUMN IF NOT EXISTS is_shared BOOLEAN NOT NULL DEFAULT FALSE;

COMMENT ON COLUMN directory_node.is_shared IS '是否共享给其他用户';

-- 共享查询索引
CREATE INDEX IF NOT EXISTS idx_dir_node_shared ON directory_node(user_id, is_shared);
