-- V4: 预习任务系统
CREATE TABLE IF NOT EXISTS public.preview_task (
    id BIGINT NOT NULL,
    class_id BIGINT NOT NULL,
    teacher_id BIGINT NOT NULL,
    title VARCHAR(200),
    knowledge_point VARCHAR(200),
    guide_text TEXT,
    questions_json JSONB,
    discussion_question TEXT,
    status VARCHAR(20) DEFAULT 'ACTIVE' NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL
);
COMMENT ON TABLE public.preview_task IS 'AI生成的课前预习任务';
CREATE SEQUENCE IF NOT EXISTS public.preview_task_id_seq START WITH 1 INCREMENT BY 1 NO MINVALUE NO MAXVALUE CACHE 1;
ALTER SEQUENCE public.preview_task_id_seq OWNED BY public.preview_task.id;
ALTER TABLE ONLY public.preview_task ALTER COLUMN id SET DEFAULT nextval('public.preview_task_id_seq'::regclass);
ALTER TABLE ONLY public.preview_task ADD CONSTRAINT preview_task_pkey PRIMARY KEY (id);
ALTER TABLE ONLY public.preview_task ADD CONSTRAINT fk_preview_class FOREIGN KEY (class_id) REFERENCES public.class_info(id);
ALTER TABLE ONLY public.preview_task ADD CONSTRAINT fk_preview_teacher FOREIGN KEY (teacher_id) REFERENCES public.sys_user(id);
CREATE INDEX IF NOT EXISTS idx_preview_class ON public.preview_task USING btree (class_id, status);
CREATE TRIGGER update_preview_task_updated_at BEFORE UPDATE ON public.preview_task FOR EACH ROW EXECUTE FUNCTION public.update_updated_at_column();
