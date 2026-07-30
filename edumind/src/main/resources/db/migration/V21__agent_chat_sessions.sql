CREATE TABLE IF NOT EXISTS public.chat_session (
    session_id character varying(64) PRIMARY KEY,
    user_id bigint NOT NULL,
    title character varying(120) NOT NULL DEFAULT '新对话',
    class_id bigint,
    course_id bigint,
    selected_kb_ids text NOT NULL DEFAULT '',
    mode character varying(32) NOT NULL DEFAULT 'auto',
    pinned boolean NOT NULL DEFAULT false,
    created_at timestamp without time zone NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at timestamp without time zone NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_chat_session_user FOREIGN KEY (user_id)
        REFERENCES public.sys_user(id) ON DELETE CASCADE,
    CONSTRAINT fk_chat_session_class FOREIGN KEY (class_id)
        REFERENCES public.class_info(id) ON DELETE SET NULL,
    CONSTRAINT fk_chat_session_course FOREIGN KEY (course_id)
        REFERENCES public.course(id) ON DELETE SET NULL
);

CREATE INDEX IF NOT EXISTS idx_chat_session_user_updated
    ON public.chat_session(user_id, pinned DESC, updated_at DESC);

WITH first_messages AS (
    SELECT DISTINCT ON (user_id, session_id)
           user_id,
           session_id,
           LEFT(REPLACE(REPLACE(content, E'\n', ' '), E'\r', ' '), 60) AS title,
           created_at
    FROM public.chat_history
    WHERE role = 'user'
    ORDER BY user_id, session_id, created_at, id
), session_activity AS (
    SELECT history.user_id,
           history.session_id,
           MIN(history.created_at) AS created_at,
           MAX(history.created_at) AS updated_at
    FROM public.chat_history history
    INNER JOIN public.sys_user existing_user
            ON existing_user.id = history.user_id
    GROUP BY history.user_id, history.session_id
)
INSERT INTO public.chat_session
    (session_id, user_id, title, created_at, updated_at)
SELECT activity.session_id,
       activity.user_id,
       COALESCE(NULLIF(first_messages.title, ''), '历史对话'),
       activity.created_at,
       activity.updated_at
FROM session_activity activity
LEFT JOIN first_messages
  ON first_messages.user_id = activity.user_id
 AND first_messages.session_id = activity.session_id
ON CONFLICT (session_id) DO NOTHING;
