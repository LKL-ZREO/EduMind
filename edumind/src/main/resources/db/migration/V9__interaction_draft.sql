-- V9: 允许互动存为草稿（课前生成，session_id 为 NULL）
ALTER TABLE public.interaction DROP CONSTRAINT IF EXISTS fk_interaction_session;
ALTER TABLE public.interaction ALTER COLUMN session_id DROP NOT NULL;
