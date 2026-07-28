ALTER TABLE public.interaction ADD COLUMN IF NOT EXISTS class_id BIGINT;
CREATE INDEX IF NOT EXISTS idx_interaction_class_draft ON public.interaction(class_id, status) WHERE status = 'DRAFT';
