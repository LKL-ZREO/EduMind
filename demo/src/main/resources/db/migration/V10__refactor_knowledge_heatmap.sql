-- ============================================
-- V10: 重构知识点热力图 - 教师自定义 + 错误分类
-- ============================================

-- ============================================
-- 1. 新建：教师自定义知识点表
-- ============================================
CREATE TABLE IF NOT EXISTS teacher_knowledge (
    id BIGSERIAL PRIMARY KEY,
    class_id BIGINT NOT NULL,
    name VARCHAR(100) NOT NULL,
    color VARCHAR(20) DEFAULT '#1890ff',
    sort_order INT NOT NULL DEFAULT 0,
    created_by BIGINT NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_class_knowledge UNIQUE (class_id, name),
    CONSTRAINT fk_tk_class FOREIGN KEY (class_id) REFERENCES class_info(id) ON DELETE CASCADE
);

COMMENT ON TABLE teacher_knowledge IS '教师自定义热力图知识点';
COMMENT ON COLUMN teacher_knowledge.class_id IS '班级ID';
COMMENT ON COLUMN teacher_knowledge.name IS '知识点名称';
COMMENT ON COLUMN teacher_knowledge.color IS '热力图颜色（十六进制）';
COMMENT ON COLUMN teacher_knowledge.sort_order IS '排序号';
COMMENT ON COLUMN teacher_knowledge.created_by IS '创建教师ID';

CREATE INDEX IF NOT EXISTS idx_teacher_knowledge_class ON teacher_knowledge(class_id);

-- ============================================
-- 2. 新建：错误分类明细表
-- ============================================
CREATE TABLE IF NOT EXISTS submission_errors (
    id BIGSERIAL PRIMARY KEY,
    submission_id BIGINT NOT NULL,
    class_id BIGINT NOT NULL,
    error_text VARCHAR(500) NOT NULL,
    error_type VARCHAR(50) DEFAULT '',
    severity VARCHAR(20) DEFAULT 'minor',
    knowledge_point VARCHAR(100) NOT NULL DEFAULT '其他',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_se_submission FOREIGN KEY (submission_id) REFERENCES submission(id) ON DELETE CASCADE,
    CONSTRAINT fk_se_class FOREIGN KEY (class_id) REFERENCES class_info(id) ON DELETE CASCADE
);

COMMENT ON TABLE submission_errors IS '作业错误分类明细（知识点归属）';
COMMENT ON COLUMN submission_errors.submission_id IS '提交ID';
COMMENT ON COLUMN submission_errors.class_id IS '班级ID';
COMMENT ON COLUMN submission_errors.error_text IS '错误描述文本';
COMMENT ON COLUMN submission_errors.error_type IS '错误类型：语法错误/逻辑错误/内存错误等';
COMMENT ON COLUMN submission_errors.severity IS '严重程度：critical/major/minor';
COMMENT ON COLUMN submission_errors.knowledge_point IS '归属知识点（关联teacher_knowledge.name），匹配不上或未定义时填"其他"';

CREATE INDEX IF NOT EXISTS idx_se_submission ON submission_errors(submission_id);
CREATE INDEX IF NOT EXISTS idx_se_class ON submission_errors(class_id);
CREATE INDEX IF NOT EXISTS idx_se_knowledge_point ON submission_errors(knowledge_point);
CREATE INDEX IF NOT EXISTS idx_se_class_kp ON submission_errors(class_id, knowledge_point);

-- ============================================
-- 3. 删除：旧 homework_knowledge 表（已废弃）
-- ============================================
DROP TABLE IF EXISTS homework_knowledge CASCADE;

-- 移除 submission 表中对 homework_knowledge 的索引引用
DROP INDEX IF EXISTS idx_hk_submission_id;
