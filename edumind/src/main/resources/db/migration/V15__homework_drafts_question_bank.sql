CREATE TABLE IF NOT EXISTS public.homework_draft (
    id BIGSERIAL PRIMARY KEY,
    teacher_id BIGINT NOT NULL,
    task_name VARCHAR(200) NOT NULL DEFAULT '',
    description TEXT,
    deadline TIMESTAMP WITHOUT TIME ZONE,
    allow_late BOOLEAN DEFAULT TRUE,
    late_penalty INTEGER DEFAULT 0,
    status VARCHAR(20) DEFAULT 'draft',
    created_at TIMESTAMP WITHOUT TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITHOUT TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS public.question_bank_item (
    id BIGSERIAL PRIMARY KEY,
    teacher_id BIGINT NOT NULL,
    title VARCHAR(200) NOT NULL,
    requirement TEXT,
    score INTEGER DEFAULT 0,
    upload_required BOOLEAN DEFAULT TRUE,
    created_at TIMESTAMP WITHOUT TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITHOUT TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS public.homework_draft_question (
    id BIGSERIAL PRIMARY KEY,
    draft_id BIGINT NOT NULL,
    question_id BIGINT NOT NULL,
    sort_order INTEGER NOT NULL DEFAULT 0,
    created_at TIMESTAMP WITHOUT TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_homework_draft_question_draft
        FOREIGN KEY (draft_id) REFERENCES public.homework_draft(id) ON DELETE CASCADE,
    CONSTRAINT fk_homework_draft_question_question
        FOREIGN KEY (question_id) REFERENCES public.question_bank_item(id) ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS idx_homework_draft_teacher_updated
    ON public.homework_draft (teacher_id, updated_at DESC);

CREATE INDEX IF NOT EXISTS idx_question_bank_teacher_updated
    ON public.question_bank_item (teacher_id, updated_at DESC);

CREATE INDEX IF NOT EXISTS idx_homework_draft_question_draft
    ON public.homework_draft_question (draft_id, sort_order);
