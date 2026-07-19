-- V2: 课堂实时互动系统
CREATE TABLE IF NOT EXISTS public.classroom_session (
    id BIGINT NOT NULL, class_id BIGINT NOT NULL, teacher_id BIGINT NOT NULL,
    session_code VARCHAR(6) NOT NULL, title VARCHAR(200), course_id BIGINT,
    status VARCHAR(20) DEFAULT 'ACTIVE' NOT NULL,
    started_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,
    ended_at TIMESTAMP, created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL
);
COMMENT ON TABLE public.classroom_session IS '课堂实时会话';
CREATE SEQUENCE public.classroom_session_id_seq START WITH 1 INCREMENT BY 1 NO MINVALUE NO MAXVALUE CACHE 1;
ALTER SEQUENCE public.classroom_session_id_seq OWNED BY public.classroom_session.id;
ALTER TABLE ONLY public.classroom_session ALTER COLUMN id SET DEFAULT nextval('public.classroom_session_id_seq'::regclass);
ALTER TABLE ONLY public.classroom_session ADD CONSTRAINT classroom_session_pkey PRIMARY KEY (id);
ALTER TABLE ONLY public.classroom_session ADD CONSTRAINT uq_session_code UNIQUE (session_code);
ALTER TABLE ONLY public.classroom_session ADD CONSTRAINT fk_session_class FOREIGN KEY (class_id) REFERENCES public.class_info(id);
ALTER TABLE ONLY public.classroom_session ADD CONSTRAINT fk_session_teacher FOREIGN KEY (teacher_id) REFERENCES public.sys_user(id);
CREATE INDEX idx_session_class ON public.classroom_session USING btree (class_id);
CREATE INDEX idx_session_status ON public.classroom_session USING btree (class_id, status);
CREATE TRIGGER update_classroom_session_updated_at BEFORE UPDATE ON public.classroom_session FOR EACH ROW EXECUTE FUNCTION public.update_updated_at_column();

CREATE TABLE IF NOT EXISTS public.interaction (
    id BIGINT NOT NULL, session_id BIGINT NOT NULL, type VARCHAR(20) NOT NULL,
    title VARCHAR(500) NOT NULL, description TEXT, options JSONB,
    correct_key VARCHAR(10), time_limit INT, status VARCHAR(20) DEFAULT 'DRAFT' NOT NULL,
    sort_order INT DEFAULT 0, ai_generated BOOLEAN DEFAULT FALSE,
    knowledge_point VARCHAR(100), created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,
    closed_at TIMESTAMP
);
COMMENT ON TABLE public.interaction IS '课堂互动题目';
CREATE SEQUENCE public.interaction_id_seq START WITH 1 INCREMENT BY 1 NO MINVALUE NO MAXVALUE CACHE 1;
ALTER SEQUENCE public.interaction_id_seq OWNED BY public.interaction.id;
ALTER TABLE ONLY public.interaction ALTER COLUMN id SET DEFAULT nextval('public.interaction_id_seq'::regclass);
ALTER TABLE ONLY public.interaction ADD CONSTRAINT interaction_pkey PRIMARY KEY (id);
ALTER TABLE ONLY public.interaction ADD CONSTRAINT fk_interaction_session FOREIGN KEY (session_id) REFERENCES public.classroom_session(id) ON DELETE CASCADE;
CREATE INDEX idx_interaction_session ON public.interaction USING btree (session_id, sort_order);

CREATE TABLE IF NOT EXISTS public.interaction_response (
    id BIGINT NOT NULL, interaction_id BIGINT NOT NULL, session_id BIGINT NOT NULL,
    student_id VARCHAR(32) NOT NULL, student_name VARCHAR(64) NOT NULL,
    answer TEXT, is_correct BOOLEAN, score INT,
    responded_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL
);
CREATE SEQUENCE public.interaction_response_id_seq START WITH 1 INCREMENT BY 1 NO MINVALUE NO MAXVALUE CACHE 1;
ALTER SEQUENCE public.interaction_response_id_seq OWNED BY public.interaction_response.id;
ALTER TABLE ONLY public.interaction_response ALTER COLUMN id SET DEFAULT nextval('public.interaction_response_id_seq'::regclass);
ALTER TABLE ONLY public.interaction_response ADD CONSTRAINT interaction_response_pkey PRIMARY KEY (id);
ALTER TABLE ONLY public.interaction_response ADD CONSTRAINT uk_response_interaction_student UNIQUE (interaction_id, student_id);
ALTER TABLE ONLY public.interaction_response ADD CONSTRAINT fk_response_interaction FOREIGN KEY (interaction_id) REFERENCES public.interaction(id) ON DELETE CASCADE;
ALTER TABLE ONLY public.interaction_response ADD CONSTRAINT fk_response_session FOREIGN KEY (session_id) REFERENCES public.classroom_session(id) ON DELETE CASCADE;
CREATE INDEX idx_response_interaction ON public.interaction_response USING btree (interaction_id);

CREATE TABLE IF NOT EXISTS public.classroom_qa (
    id BIGINT NOT NULL, session_id BIGINT NOT NULL, question TEXT NOT NULL,
    student_id VARCHAR(32) NOT NULL, student_name VARCHAR(64),
    is_answered BOOLEAN DEFAULT FALSE, answer_text TEXT,
    similar_to BIGINT, similar_count INT DEFAULT 0,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL
);
CREATE SEQUENCE public.classroom_qa_id_seq START WITH 1 INCREMENT BY 1 NO MINVALUE NO MAXVALUE CACHE 1;
ALTER SEQUENCE public.classroom_qa_id_seq OWNED BY public.classroom_qa.id;
ALTER TABLE ONLY public.classroom_qa ALTER COLUMN id SET DEFAULT nextval('public.classroom_qa_id_seq'::regclass);
ALTER TABLE ONLY public.classroom_qa ADD CONSTRAINT classroom_qa_pkey PRIMARY KEY (id);
ALTER TABLE ONLY public.classroom_qa ADD CONSTRAINT fk_qa_session FOREIGN KEY (session_id) REFERENCES public.classroom_session(id) ON DELETE CASCADE;
CREATE INDEX idx_qa_session ON public.classroom_qa USING btree (session_id, is_answered);
