CREATE TABLE IF NOT EXISTS agent_chat_memory (
    memory_key VARCHAR(64) PRIMARY KEY,
    user_id BIGINT,
    session_id VARCHAR(255) NOT NULL,
    messages_json JSONB NOT NULL DEFAULT '[]'::jsonb,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_agent_chat_memory_user
    ON agent_chat_memory (user_id);

CREATE INDEX IF NOT EXISTS idx_agent_chat_memory_session
    ON agent_chat_memory (session_id);

COMMENT ON TABLE agent_chat_memory IS
    'Token-bounded LangChain4j working memory snapshots; separate from user-visible chat history';
