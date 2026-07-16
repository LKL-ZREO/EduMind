-- V5: 学生"不懂"标记记录 — 教师在 Dashboard 可查看学生通过 QQ 标记的知识点疑问
CREATE TABLE IF NOT EXISTS public.student_confusion_log (
    id BIGINT NOT NULL,
    qq_number VARCHAR(20) NOT NULL,
    student_name VARCHAR(100),
    class_id BIGINT,
    question TEXT,
    knowledge_point VARCHAR(200),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL
);
COMMENT ON TABLE public.student_confusion_log IS '学生通过QQ标记"不懂"的知识点记录';
CREATE SEQUENCE IF NOT EXISTS public.student_confusion_log_id_seq START WITH 1 INCREMENT BY 1 NO MINVALUE NO MAXVALUE CACHE 1;
ALTER SEQUENCE public.student_confusion_log_id_seq OWNED BY public.student_confusion_log.id;
ALTER TABLE ONLY public.student_confusion_log ALTER COLUMN id SET DEFAULT nextval('public.student_confusion_log_id_seq'::regclass);
ALTER TABLE ONLY public.student_confusion_log ADD CONSTRAINT student_confusion_log_pkey PRIMARY KEY (id);
CREATE INDEX IF NOT EXISTS idx_confusion_class ON public.student_confusion_log USING btree (class_id, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_confusion_kp ON public.student_confusion_log USING btree (class_id, knowledge_point);
