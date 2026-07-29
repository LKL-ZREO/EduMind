ALTER TABLE public.classroom_session
    ADD COLUMN IF NOT EXISTS teacher_offline_at TIMESTAMP WITHOUT TIME ZONE;

-- 部署重启时，既有 ACTIVE 课堂先进入离线宽限期；教师重新连接后会清空该字段。
UPDATE public.classroom_session
SET teacher_offline_at = CURRENT_TIMESTAMP
WHERE status = 'ACTIVE' AND teacher_offline_at IS NULL;

CREATE INDEX IF NOT EXISTS idx_classroom_session_teacher_offline
    ON public.classroom_session(teacher_offline_at)
    WHERE status = 'ACTIVE' AND teacher_offline_at IS NOT NULL;
