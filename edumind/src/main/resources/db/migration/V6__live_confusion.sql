-- V6: 课堂实时"不懂"标记 — 学生对课堂推送题目标记不懂，AI即时反馈，课后教师查看汇总
CREATE TABLE IF NOT EXISTS public.live_confusion_event (
    id BIGINT NOT NULL,
    session_id BIGINT NOT NULL,
    interaction_id BIGINT,
    student_id VARCHAR(50) NOT NULL,
    student_name VARCHAR(100),
    knowledge_point VARCHAR(200),
    question_text TEXT,
    ai_explanation TEXT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL
);
COMMENT ON TABLE public.live_confusion_event IS '课堂实时互动中学生标记"不懂"的事件';
CREATE SEQUENCE IF NOT EXISTS public.live_confusion_event_id_seq START WITH 1 INCREMENT BY 1 NO MINVALUE NO MAXVALUE CACHE 1;
ALTER SEQUENCE public.live_confusion_event_id_seq OWNED BY public.live_confusion_event.id;
ALTER TABLE ONLY public.live_confusion_event ALTER COLUMN id SET DEFAULT nextval('public.live_confusion_event_id_seq'::regclass);
ALTER TABLE ONLY public.live_confusion_event ADD CONSTRAINT live_confusion_event_pkey PRIMARY KEY (id);
CREATE INDEX IF NOT EXISTS idx_confusion_session ON public.live_confusion_event USING btree (session_id, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_confusion_session_kp ON public.live_confusion_event USING btree (session_id, knowledge_point);
