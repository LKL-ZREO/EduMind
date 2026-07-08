-- 作业评价表
CREATE TABLE homework_evaluation (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL,
    session_id VARCHAR(64) NOT NULL,
    file_path VARCHAR(500),
    requirement TEXT,
    
    -- 评分字段
    total_score INTEGER,           -- 总分
    content_score INTEGER,         -- 内容评分
    format_score INTEGER,          -- 格式评分
    
    -- 评语
    overall_comment TEXT,          -- 总体评语
    strengths TEXT,                -- 优点（JSON数组或逗号分隔）
    weaknesses TEXT,               -- 缺点（JSON数组或逗号分隔）
    suggestions TEXT,              -- 改进建议
    
    -- 原始响应
    raw_response TEXT,
    
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    
    CONSTRAINT fk_user FOREIGN KEY (user_id) REFERENCES sys_user(id)
);

-- 索引
CREATE INDEX idx_eval_user_id ON homework_evaluation(user_id);
CREATE INDEX idx_eval_session_id ON homework_evaluation(session_id);
