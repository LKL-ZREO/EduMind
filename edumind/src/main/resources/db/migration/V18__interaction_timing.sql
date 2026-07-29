ALTER TABLE public.interaction
    ADD COLUMN IF NOT EXISTS activated_at TIMESTAMP WITHOUT TIME ZONE,
    ADD COLUMN IF NOT EXISTS deadline_at TIMESTAMP WITHOUT TIME ZONE;

UPDATE public.interaction
SET activated_at = created_at
WHERE status <> 'DRAFT' AND activated_at IS NULL;

UPDATE public.interaction
SET deadline_at = activated_at + (time_limit * INTERVAL '1 second')
WHERE status = 'ACTIVE'
  AND deadline_at IS NULL
  AND activated_at IS NOT NULL
  AND time_limit IS NOT NULL
  AND time_limit > 0;

CREATE INDEX IF NOT EXISTS idx_interaction_active_deadline
    ON public.interaction(deadline_at)
    WHERE status = 'ACTIVE' AND deadline_at IS NOT NULL;
