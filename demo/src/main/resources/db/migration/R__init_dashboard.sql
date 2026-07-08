-- ============================================
-- 学习管理平台数据库初始化脚本 (PostgreSQL)
-- ============================================

-- 1. 班级信息表
CREATE TABLE IF NOT EXISTS class_info (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(50) NOT NULL,
    teacher_id BIGINT,
    description VARCHAR(200),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

COMMENT ON TABLE class_info IS '班级信息表';
COMMENT ON COLUMN class_info.id IS '班级ID';
COMMENT ON COLUMN class_info.name IS '班级名称';
COMMENT ON COLUMN class_info.teacher_id IS '班主任ID（关联sys_user）';
COMMENT ON COLUMN class_info.description IS '班级描述';

-- 2. 用户表添加班级字段
ALTER TABLE sys_user ADD COLUMN IF NOT EXISTS class_id BIGINT;
COMMENT ON COLUMN sys_user.class_id IS '所属班级ID（学生必填）';

-- 3. 作业评价表添加班级字段
ALTER TABLE homework_evaluation ADD COLUMN IF NOT EXISTS class_id BIGINT;
COMMENT ON COLUMN homework_evaluation.class_id IS '班级ID';

-- ============================================
-- 初始化测试数据
-- ============================================

-- 插入测试班级
INSERT INTO class_info (id, name, teacher_id, description) VALUES
(1, '计算机一班', NULL, '2023级计算机科学与技术1班'),
(2, '计算机二班', NULL, '2023级计算机科学与技术2班'),
(3, '软件工程班', NULL, '2023级软件工程班')
ON CONFLICT (id) DO UPDATE SET name = EXCLUDED.name;

-- ============================================
-- 数据迁移（根据实际情况调整）
-- ============================================

-- 查看当前用户情况
-- SELECT id, username, status FROM sys_user;

-- 将前3个学生分配到班级1（请根据实际ID修改）
-- UPDATE sys_user SET class_id = 1 WHERE id IN (1, 2, 3) AND status = 1;

-- 将其他学生分配到班级2（请根据实际ID修改）
-- UPDATE sys_user SET class_id = 2 WHERE id IN (4, 5, 6) AND status = 1;

-- 将老师设置为班主任（请根据实际ID修改）
-- UPDATE class_info SET teacher_id = 4 WHERE id = 1;

-- 更新历史作业评价的班级ID（根据user_id关联）
UPDATE homework_evaluation he
SET class_id = u.class_id
FROM sys_user u
WHERE he.user_id = u.id
  AND he.class_id IS NULL
  AND u.class_id IS NOT NULL;
