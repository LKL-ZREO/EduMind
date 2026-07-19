ALTER TABLE public.preview_task ADD COLUMN IF NOT EXISTS source_doc_id VARCHAR(64);
ALTER TABLE public.interaction ADD COLUMN IF NOT EXISTS source_doc_id VARCHAR(64);
CREATE INDEX IF NOT EXISTS idx_preview_source_doc ON public.preview_task(source_doc_id);
CREATE INDEX IF NOT EXISTS idx_interaction_source_doc ON public.interaction(source_doc_id);
