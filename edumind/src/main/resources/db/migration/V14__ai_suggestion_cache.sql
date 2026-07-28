-- V14: AI 备课建议缓存表 — 持久化 LLM 生成的备课建议，按班级缓存
CREATE TABLE IF NOT EXISTS ai_suggestion_cache (
    class_id BIGINT NOT NULL PRIMARY KEY,
    suggestion TEXT NOT NULL,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL
);
COMMENT ON TABLE ai_suggestion_cache IS 'AI备课建议缓存，按班级存储LLM生成的备课建议';
