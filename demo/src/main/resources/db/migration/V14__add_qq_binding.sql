-- 学生QQ绑定表
CREATE TABLE IF NOT EXISTS student_qq_binding (
    student_id VARCHAR(32) PRIMARY KEY,
    qq_number VARCHAR(32) NOT NULL,
    student_name VARCHAR(64),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- 班级QQ群号
ALTER TABLE class_info ADD COLUMN IF NOT EXISTS qq_group_id VARCHAR(32);

-- 班级学生表
CREATE TABLE IF NOT EXISTS class_student (
    id BIGSERIAL PRIMARY KEY,
    class_id BIGINT NOT NULL,
    student_id VARCHAR(32) NOT NULL,
    student_name VARCHAR(64) NOT NULL,
    source VARCHAR(20) DEFAULT 'auto',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    UNIQUE(class_id, student_id)
);

CREATE INDEX IF NOT EXISTS idx_class_student_class ON class_student(class_id);
CREATE INDEX IF NOT EXISTS idx_class_student_student ON class_student(student_id);
