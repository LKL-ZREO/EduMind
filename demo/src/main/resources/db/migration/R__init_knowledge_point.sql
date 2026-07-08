-- ============================================
-- 知识点相关表结构 (PostgreSQL)
-- ============================================

-- 1. 知识点字典表（已废弃，保留用于参考）
CREATE TABLE IF NOT EXISTS knowledge_point (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(100) NOT NULL UNIQUE,
    subject VARCHAR(50),
    category VARCHAR(50),
    description TEXT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

COMMENT ON TABLE knowledge_point IS '知识点字典表（已废弃）';
COMMENT ON COLUMN knowledge_point.name IS '知识点名称';
COMMENT ON COLUMN knowledge_point.subject IS '所属学科';
COMMENT ON COLUMN knowledge_point.category IS '分类';

-- 2. 初始化常用知识点（Java相关）
INSERT INTO knowledge_point (name, subject, category) VALUES
('Java基础语法', 'Java', '基础'),
('面向对象', 'Java', '基础'),
('集合框架', 'Java', '进阶'),
('异常处理', 'Java', '基础'),
('IO流', 'Java', '进阶'),
('多线程', 'Java', '进阶'),
('JVM原理', 'Java', '高级'),
('Spring Boot', 'Java', '框架'),
('MySQL', '数据库', '基础'),
('Redis', '数据库', '进阶'),
('Git', '工具', '基础'),
('设计模式', 'Java', '高级'),
('数据结构与算法', '计算机基础', '基础'),
('网络编程', 'Java', '进阶'),
('Maven/Gradle', '工具', '基础')
ON CONFLICT (name) DO NOTHING;
