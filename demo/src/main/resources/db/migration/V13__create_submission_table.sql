-- 作业提交表（学生端匿名提交）
CREATE TABLE IF NOT EXISTS submission (
    id BIGSERIAL PRIMARY KEY,
    student_name VARCHAR(100) NOT NULL,
    class_name VARCHAR(100) NOT NULL,
    class_id BIGINT NOT NULL,
    assignment_name VARCHAR(200) NOT NULL,
    file_name VARCHAR(255) NOT NULL,
    file_path TEXT NOT NULL,
    file_size BIGINT,
    total_score INTEGER,
    content_score INTEGER,
    format_score INTEGER,
    overall_comment TEXT,
    strengths TEXT,
    weaknesses TEXT,
    suggestions TEXT,
    raw_response TEXT,
    submitted_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_submission_class FOREIGN KEY (class_id) REFERENCES class_info(id)
);

-- 为 homework_knowledge 表添加 submission_id 字段
ALTER TABLE homework_knowledge ADD COLUMN IF NOT EXISTS submission_id BIGINT;

-- evaluation_id 改为可空（新 submission 数据用 submission_id 关联）
ALTER TABLE homework_knowledge ALTER COLUMN evaluation_id DROP NOT NULL;

-- 索引
CREATE INDEX IF NOT EXISTS idx_submission_class_id ON submission(class_id);
CREATE INDEX IF NOT EXISTS idx_submission_class_name ON submission(class_name);
CREATE INDEX IF NOT EXISTS idx_submission_student_name ON submission(student_name);
CREATE INDEX IF NOT EXISTS idx_hk_submission_id ON homework_knowledge(submission_id);
