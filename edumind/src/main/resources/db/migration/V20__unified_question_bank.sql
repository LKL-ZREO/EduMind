-- 统一题库：题目只保存在 question_bank_item，interaction 只保存课堂发送快照。
ALTER TABLE public.question_bank_item
    ALTER COLUMN title TYPE VARCHAR(4000),
    ADD COLUMN IF NOT EXISTS type VARCHAR(20) NOT NULL DEFAULT 'HOMEWORK',
    ADD COLUMN IF NOT EXISTS options JSONB,
    ADD COLUMN IF NOT EXISTS correct_key TEXT,
    ADD COLUMN IF NOT EXISTS explanation TEXT,
    ADD COLUMN IF NOT EXISTS knowledge_point VARCHAR(500),
    ADD COLUMN IF NOT EXISTS difficulty VARCHAR(16),
    ADD COLUMN IF NOT EXISTS default_time_limit INTEGER,
    ADD COLUMN IF NOT EXISTS source_doc_id VARCHAR(64),
    ADD COLUMN IF NOT EXISTS ai_generated BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN IF NOT EXISTS archived BOOLEAN NOT NULL DEFAULT FALSE;

ALTER TABLE public.interaction
    ADD COLUMN IF NOT EXISTS question_id BIGINT,
    ADD COLUMN IF NOT EXISTS difficulty VARCHAR(16),
    ADD COLUMN IF NOT EXISTS explanation TEXT;

-- 将旧课堂草稿和历史互动关联到统一题库。AI 生成流程曾经双写两张表，
-- 对创建时间接近且标题一致的记录优先复用原题库项，避免制造第二份题目。
DO $$
DECLARE
    interaction_row RECORD;
    matched_question_id BIGINT;
BEGIN
    FOR interaction_row IN
        SELECT i.*,
               COALESCE(ci.teacher_id, cs.teacher_id) AS owner_teacher_id
        FROM public.interaction i
        LEFT JOIN public.class_info ci ON ci.id = i.class_id
        LEFT JOIN public.classroom_session cs ON cs.id = i.session_id
        WHERE COALESCE(ci.teacher_id, cs.teacher_id) IS NOT NULL
        ORDER BY i.id
    LOOP
        matched_question_id := NULL;

        SELECT q.id
        INTO matched_question_id
        FROM public.question_bank_item q
        WHERE q.teacher_id = interaction_row.owner_teacher_id
          AND q.title = interaction_row.title
          AND q.archived = FALSE
          AND q.requirement LIKE '类型:%'
          AND ABS(EXTRACT(EPOCH FROM (q.created_at - interaction_row.created_at))) <= 300
        ORDER BY ABS(EXTRACT(EPOCH FROM (q.created_at - interaction_row.created_at))), q.id
        LIMIT 1;

        IF matched_question_id IS NULL THEN
            INSERT INTO public.question_bank_item (
                teacher_id, type, title, requirement, options, correct_key,
                explanation, knowledge_point, difficulty, default_time_limit,
                score, upload_required, source_doc_id, ai_generated, archived,
                created_at, updated_at
            ) VALUES (
                interaction_row.owner_teacher_id,
                interaction_row.type,
                interaction_row.title,
                interaction_row.description,
                interaction_row.options,
                interaction_row.correct_key,
                interaction_row.explanation,
                interaction_row.knowledge_point,
                interaction_row.difficulty,
                interaction_row.time_limit,
                10,
                FALSE,
                interaction_row.source_doc_id,
                COALESCE(interaction_row.ai_generated, FALSE),
                FALSE,
                interaction_row.created_at,
                interaction_row.created_at
            )
            RETURNING id INTO matched_question_id;
        ELSE
            UPDATE public.question_bank_item
            SET type = interaction_row.type,
                requirement = interaction_row.description,
                options = interaction_row.options,
                correct_key = interaction_row.correct_key,
                explanation = interaction_row.explanation,
                knowledge_point = interaction_row.knowledge_point,
                difficulty = interaction_row.difficulty,
                default_time_limit = interaction_row.time_limit,
                source_doc_id = interaction_row.source_doc_id,
                ai_generated = COALESCE(interaction_row.ai_generated, FALSE),
                upload_required = FALSE,
                updated_at = GREATEST(updated_at, interaction_row.created_at)
            WHERE id = matched_question_id;
        END IF;

        UPDATE public.interaction
        SET question_id = matched_question_id
        WHERE id = interaction_row.id;
    END LOOP;
END $$;

-- 未发送的旧 Interaction 已完整迁移为题库项；真正的 Interaction 从发送时才创建。
DELETE FROM public.interaction WHERE status = 'DRAFT';

ALTER TABLE public.interaction
    ALTER COLUMN session_id SET NOT NULL,
    ALTER COLUMN question_id SET NOT NULL,
    ALTER COLUMN status SET DEFAULT 'ACTIVE';

ALTER TABLE public.interaction
    ADD CONSTRAINT fk_interaction_session
        FOREIGN KEY (session_id) REFERENCES public.classroom_session(id) ON DELETE CASCADE,
    ADD CONSTRAINT fk_interaction_question
        FOREIGN KEY (question_id) REFERENCES public.question_bank_item(id);

DROP INDEX IF EXISTS public.idx_interaction_class_draft;

CREATE INDEX IF NOT EXISTS idx_question_bank_teacher_active
    ON public.question_bank_item (teacher_id, archived, updated_at DESC);
CREATE INDEX IF NOT EXISTS idx_question_bank_source_doc
    ON public.question_bank_item (source_doc_id, archived, updated_at DESC);
CREATE INDEX IF NOT EXISTS idx_question_bank_type
    ON public.question_bank_item (teacher_id, type, archived);
CREATE INDEX IF NOT EXISTS idx_interaction_question
    ON public.interaction (question_id, created_at DESC);
