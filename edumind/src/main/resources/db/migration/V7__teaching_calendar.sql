-- V7: 教学日历 — 教师按周规划教学主题，课后回填课堂数据
CREATE TABLE IF NOT EXISTS public.teaching_calendar (
    id BIGINT NOT NULL,
    class_id BIGINT NOT NULL,
    teacher_id BIGINT NOT NULL,
    week_number INT NOT NULL,
    planned_date DATE,
    topic VARCHAR(200) NOT NULL,
    knowledge_points TEXT,
    session_id BIGINT,
    status VARCHAR(20) DEFAULT 'PLANNED' NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL
);
COMMENT ON TABLE public.teaching_calendar IS '教师教学日历：计划 + 回填课堂结果';
CREATE SEQUENCE IF NOT EXISTS public.teaching_calendar_id_seq START WITH 1 INCREMENT BY 1 NO MINVALUE NO MAXVALUE CACHE 1;
ALTER SEQUENCE public.teaching_calendar_id_seq OWNED BY public.teaching_calendar.id;
ALTER TABLE ONLY public.teaching_calendar ALTER COLUMN id SET DEFAULT nextval('public.teaching_calendar_id_seq'::regclass);
ALTER TABLE ONLY public.teaching_calendar ADD CONSTRAINT teaching_calendar_pkey PRIMARY KEY (id);
ALTER TABLE ONLY public.teaching_calendar ADD CONSTRAINT fk_calendar_class FOREIGN KEY (class_id) REFERENCES public.class_info(id);
ALTER TABLE ONLY public.teaching_calendar ADD CONSTRAINT fk_calendar_teacher FOREIGN KEY (teacher_id) REFERENCES public.sys_user(id);
CREATE INDEX IF NOT EXISTS idx_calendar_class ON public.teaching_calendar USING btree (class_id, week_number);
CREATE TRIGGER update_teaching_calendar_updated_at BEFORE UPDATE ON public.teaching_calendar FOR EACH ROW EXECUTE FUNCTION public.update_updated_at_column();
