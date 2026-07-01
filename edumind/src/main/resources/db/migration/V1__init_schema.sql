CREATE EXTENSION IF NOT EXISTS pg_trgm WITH SCHEMA public;


--
-- Name: EXTENSION pg_trgm; Type: COMMENT; Schema: -; Owner: -
--



--
-- Name: vector; Type: EXTENSION; Schema: -; Owner: -
--

CREATE EXTENSION IF NOT EXISTS vector WITH SCHEMA public;


--
-- Name: EXTENSION vector; Type: COMMENT; Schema: -; Owner: -
--



--
-- Name: update_updated_at_column(); Type: FUNCTION; Schema: public; Owner: -
--

CREATE FUNCTION public.update_updated_at_column() RETURNS trigger
    LANGUAGE plpgsql
    AS $$
BEGIN
    NEW.updated_at = CURRENT_TIMESTAMP;
    RETURN NEW;
END;
$$;




--
-- Name: chat_history; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE IF NOT EXISTS public.chat_history (
    id bigint NOT NULL,
    user_id bigint NOT NULL,
    session_id character varying(64) NOT NULL,
    role character varying(20) NOT NULL,
    content text NOT NULL,
    model character varying(50),
    tokens_used integer DEFAULT 0,
    created_at timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_at timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL
);


--
-- Name: TABLE chat_history; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON TABLE public.chat_history IS '对话记录表';


--
-- Name: COLUMN chat_history.user_id; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.chat_history.user_id IS '用户ID';


--
-- Name: COLUMN chat_history.session_id; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.chat_history.session_id IS '会话ID';


--
-- Name: COLUMN chat_history.role; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.chat_history.role IS '角色：user/assistant';


--
-- Name: COLUMN chat_history.content; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.chat_history.content IS '消息内容';


--
-- Name: COLUMN chat_history.model; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.chat_history.model IS '使用的模型';


--
-- Name: COLUMN chat_history.tokens_used; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.chat_history.tokens_used IS '使用的token数';


--
-- Name: chat_history_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.chat_history_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: chat_history_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.chat_history_id_seq OWNED BY public.chat_history.id;


--
-- Name: class_info; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE IF NOT EXISTS public.class_info (
    id bigint NOT NULL,
    name character varying(50) NOT NULL,
    teacher_id bigint,
    description character varying(200),
    created_at timestamp without time zone DEFAULT CURRENT_TIMESTAMP,
    qq_group_id character varying(32),
    course_group character varying(64) DEFAULT ''::character varying,
    status character varying(20) DEFAULT 'ACTIVE'::character varying,
    updated_at timestamp without time zone DEFAULT CURRENT_TIMESTAMP,
    invite_code character varying(8) NOT NULL,
    course_id bigint
);


--
-- Name: TABLE class_info; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON TABLE public.class_info IS '班级信息表';


--
-- Name: COLUMN class_info.id; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.class_info.id IS '班级ID';


--
-- Name: COLUMN class_info.name; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.class_info.name IS '班级名称';


--
-- Name: COLUMN class_info.teacher_id; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.class_info.teacher_id IS '班主任ID（关联sys_user）';


--
-- Name: COLUMN class_info.description; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.class_info.description IS '班级描述';


--
-- Name: class_info_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.class_info_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: class_info_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.class_info_id_seq OWNED BY public.class_info.id;


--
-- Name: class_student; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE IF NOT EXISTS public.class_student (
    id bigint NOT NULL,
    class_id bigint NOT NULL,
    student_id character varying(32) NOT NULL,
    student_name character varying(64) NOT NULL,
    source character varying(20) DEFAULT 'auto'::character varying,
    created_at timestamp without time zone DEFAULT CURRENT_TIMESTAMP
);


--
-- Name: class_student_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.class_student_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: class_student_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.class_student_id_seq OWNED BY public.class_student.id;


--
-- Name: course; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE IF NOT EXISTS public.course (
    id bigint NOT NULL,
    name character varying(100) NOT NULL,
    system_prompt text NOT NULL,
    knowledge_scope text,
    teacher_id bigint,
    created_at timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_at timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL
);


--
-- Name: course_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.course_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: course_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.course_id_seq OWNED BY public.course.id;


--
-- Name: directory_node; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE IF NOT EXISTS public.directory_node (
    id bigint NOT NULL,
    user_id bigint NOT NULL,
    parent_id bigint,
    label character varying(255) NOT NULL,
    node_type character varying(20) DEFAULT 'folder'::character varying NOT NULL,
    doc_id character varying(64) DEFAULT NULL::character varying,
    sort_order integer DEFAULT 0,
    created_at timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_at timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    is_shared boolean DEFAULT false NOT NULL,
    kb_id bigint
);


--
-- Name: TABLE directory_node; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON TABLE public.directory_node IS '目录节点（知识库目录树）';


--
-- Name: COLUMN directory_node.user_id; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.directory_node.user_id IS '所属用户ID';


--
-- Name: COLUMN directory_node.parent_id; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.directory_node.parent_id IS '父节点ID，null=根节点';


--
-- Name: COLUMN directory_node.label; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.directory_node.label IS '显示名称';


--
-- Name: COLUMN directory_node.node_type; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.directory_node.node_type IS '节点类型：folder/file';


--
-- Name: COLUMN directory_node.doc_id; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.directory_node.doc_id IS '关联文档ID（file类型）';


--
-- Name: COLUMN directory_node.sort_order; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.directory_node.sort_order IS '同级排序序号';


--
-- Name: COLUMN directory_node.is_shared; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.directory_node.is_shared IS '是否共享给其他用户';


--
-- Name: COLUMN directory_node.kb_id; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.directory_node.kb_id IS '所属共享知识库ID，null=个人目录';


--
-- Name: directory_node_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.directory_node_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: directory_node_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.directory_node_id_seq OWNED BY public.directory_node.id;


--
-- Name: document; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE IF NOT EXISTS public.document (
    id bigint NOT NULL,
    user_id bigint NOT NULL,
    doc_id character varying(64) NOT NULL,
    doc_name character varying(255) NOT NULL,
    file_path character varying(500),
    file_size bigint DEFAULT 0,
    content_type character varying(100),
    status smallint DEFAULT 1,
    chunk_count integer DEFAULT 0,
    created_at timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_at timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    kb_id bigint
);


--
-- Name: TABLE document; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON TABLE public.document IS '文档表';


--
-- Name: COLUMN document.user_id; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.document.user_id IS '上传用户ID';


--
-- Name: COLUMN document.doc_id; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.document.doc_id IS '文档唯一标识';


--
-- Name: COLUMN document.doc_name; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.document.doc_name IS '文档名称';


--
-- Name: COLUMN document.file_path; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.document.file_path IS '文件存储路径';


--
-- Name: COLUMN document.file_size; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.document.file_size IS '文件大小（字节）';


--
-- Name: COLUMN document.content_type; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.document.content_type IS '文件类型';


--
-- Name: COLUMN document.status; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.document.status IS '状态：0-处理中 1-已完成 2-失败';


--
-- Name: COLUMN document.chunk_count; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.document.chunk_count IS '切割块数';


--
-- Name: COLUMN document.kb_id; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.document.kb_id IS '所属共享知识库ID，null=个人文档';


--
-- Name: document_chunk; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE IF NOT EXISTS public.document_chunk (
    id character varying(64) NOT NULL,
    doc_id character varying(64) NOT NULL,
    doc_name character varying(255),
    chunk_index integer DEFAULT 0,
    sub_index integer DEFAULT 0,
    content text NOT NULL,
    token_count integer DEFAULT 0,
    char_count integer DEFAULT 0,
    embedding text,
    prev_summary text,
    next_summary text,
    created_at timestamp without time zone DEFAULT CURRENT_TIMESTAMP,
    embedding_vec public.vector(512),
    metadata jsonb,
    kb_id bigint,
    user_id bigint
);


--
-- Name: COLUMN document_chunk.metadata; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.document_chunk.metadata IS '文档块元数据，JSON格式存储';


--
-- Name: COLUMN document_chunk.kb_id; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.document_chunk.kb_id IS '所属知识库ID，null=个人';


--
-- Name: COLUMN document_chunk.user_id; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.document_chunk.user_id IS '上传者ID，NULL=未关联用户；user_id + kb_id IS NULL = 私人文档';


--
-- Name: document_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.document_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: document_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.document_id_seq OWNED BY public.document.id;


--
-- Name: homework_evaluation; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE IF NOT EXISTS public.homework_evaluation (
    id bigint NOT NULL,
    user_id bigint NOT NULL,
    session_id character varying(64) NOT NULL,
    file_path character varying(500),
    requirement text,
    total_score integer,
    content_score integer,
    format_score integer,
    overall_comment text,
    strengths text,
    weaknesses text,
    suggestions text,
    raw_response text,
    created_at timestamp without time zone DEFAULT CURRENT_TIMESTAMP,
    class_id bigint
);


--
-- Name: COLUMN homework_evaluation.class_id; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.homework_evaluation.class_id IS '班级ID';


--
-- Name: homework_evaluation_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.homework_evaluation_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: homework_evaluation_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.homework_evaluation_id_seq OWNED BY public.homework_evaluation.id;


--
-- Name: homework_task; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE IF NOT EXISTS public.homework_task (
    id integer NOT NULL,
    class_id bigint NOT NULL,
    task_name character varying(200) NOT NULL,
    description text,
    deadline timestamp without time zone,
    allow_late boolean DEFAULT true,
    late_penalty integer DEFAULT 0,
    status character varying(20) DEFAULT 'active'::character varying,
    created_by bigint,
    created_at timestamp without time zone DEFAULT CURRENT_TIMESTAMP,
    updated_at timestamp without time zone DEFAULT CURRENT_TIMESTAMP
);


--
-- Name: TABLE homework_task; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON TABLE public.homework_task IS '作业任务';


--
-- Name: homework_task_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.homework_task_id_seq
    AS integer
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: homework_task_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.homework_task_id_seq OWNED BY public.homework_task.id;


--
-- Name: shared_kb; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE IF NOT EXISTS public.shared_kb (
    id bigint NOT NULL,
    name character varying(100) NOT NULL,
    description character varying(500) DEFAULT ''::character varying,
    owner_id bigint NOT NULL,
    invite_token character varying(64),
    invite_expires_at timestamp without time zone,
    invite_max_uses integer DEFAULT 0,
    invite_use_count integer DEFAULT 0,
    created_at timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_at timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL
);


--
-- Name: TABLE shared_kb; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON TABLE public.shared_kb IS '共享知识库';


--
-- Name: COLUMN shared_kb.owner_id; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.shared_kb.owner_id IS '创建者ID';


--
-- Name: COLUMN shared_kb.invite_token; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.shared_kb.invite_token IS '邀请链接token';


--
-- Name: COLUMN shared_kb.invite_expires_at; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.shared_kb.invite_expires_at IS '邀请链接过期时间';


--
-- Name: shared_kb_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.shared_kb_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: shared_kb_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.shared_kb_id_seq OWNED BY public.shared_kb.id;


--
-- Name: shared_kb_member; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE IF NOT EXISTS public.shared_kb_member (
    id bigint NOT NULL,
    kb_id bigint NOT NULL,
    user_id bigint NOT NULL,
    role character varying(20) DEFAULT 'member'::character varying NOT NULL,
    joined_at timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL
);


--
-- Name: TABLE shared_kb_member; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON TABLE public.shared_kb_member IS '知识库成员';


--
-- Name: COLUMN shared_kb_member.role; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.shared_kb_member.role IS 'owner | admin | member';


--
-- Name: shared_kb_member_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.shared_kb_member_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: shared_kb_member_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.shared_kb_member_id_seq OWNED BY public.shared_kb_member.id;


--
-- Name: student_qq_binding; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE IF NOT EXISTS public.student_qq_binding (
    student_id character varying(32) NOT NULL,
    qq_number character varying(32) NOT NULL,
    student_name character varying(64),
    created_at timestamp without time zone DEFAULT CURRENT_TIMESTAMP
);


--
-- Name: submission; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE IF NOT EXISTS public.submission (
    id bigint NOT NULL,
    student_name character varying(100) NOT NULL,
    class_name character varying(100) NOT NULL,
    class_id bigint NOT NULL,
    assignment_name character varying(200) NOT NULL,
    file_name character varying(255) NOT NULL,
    file_path text NOT NULL,
    file_size bigint,
    total_score integer,
    content_score integer,
    format_score integer,
    overall_comment text,
    strengths text,
    weaknesses text,
    suggestions text,
    raw_response text,
    submitted_at timestamp without time zone DEFAULT CURRENT_TIMESTAMP,
    assignment_no integer DEFAULT 1,
    task_id bigint,
    submit_count integer DEFAULT 1,
    remaining_attempts integer DEFAULT 2,
    is_late boolean DEFAULT false,
    penalty_applied boolean DEFAULT false,
    final_score integer,
    student_id character varying(32),
    status character varying(20) DEFAULT 'PENDING'::character varying,
    error_message character varying(500)
);


--
-- Name: submission_errors; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE IF NOT EXISTS public.submission_errors (
    id bigint NOT NULL,
    submission_id bigint NOT NULL,
    class_id bigint NOT NULL,
    error_text character varying(500) NOT NULL,
    error_type character varying(50) DEFAULT ''::character varying,
    severity character varying(20) DEFAULT 'minor'::character varying,
    knowledge_point character varying(100) DEFAULT '其他'::character varying NOT NULL,
    created_at timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_at timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL
);


--
-- Name: TABLE submission_errors; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON TABLE public.submission_errors IS '作业错误分类明细（知识点归属）';


--
-- Name: COLUMN submission_errors.submission_id; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.submission_errors.submission_id IS '提交ID';


--
-- Name: COLUMN submission_errors.class_id; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.submission_errors.class_id IS '班级ID';


--
-- Name: COLUMN submission_errors.error_text; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.submission_errors.error_text IS '错误描述文本';


--
-- Name: COLUMN submission_errors.error_type; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.submission_errors.error_type IS '错误类型：语法错误/逻辑错误/内存错误等';


--
-- Name: COLUMN submission_errors.severity; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.submission_errors.severity IS '严重程度：critical/major/minor';


--
-- Name: COLUMN submission_errors.knowledge_point; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.submission_errors.knowledge_point IS '归属知识点（关联teacher_knowledge.name），匹配不上或未定义时填"其他"';


--
-- Name: submission_errors_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.submission_errors_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: submission_errors_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.submission_errors_id_seq OWNED BY public.submission_errors.id;


--
-- Name: submission_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.submission_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: submission_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.submission_id_seq OWNED BY public.submission.id;


--
-- Name: sys_user; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE IF NOT EXISTS public.sys_user (
    id bigint NOT NULL,
    username character varying(50) NOT NULL,
    password character varying(255) NOT NULL,
    phone character varying(20),
    email character varying(100),
    create_time timestamp without time zone DEFAULT CURRENT_TIMESTAMP,
    update_time timestamp without time zone DEFAULT CURRENT_TIMESTAMP,
    status integer DEFAULT 1,
    class_id bigint
);


--
-- Name: COLUMN sys_user.class_id; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.sys_user.class_id IS '所属班级ID（学生必填）';


--
-- Name: sys_user_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.sys_user_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: sys_user_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.sys_user_id_seq OWNED BY public.sys_user.id;


--
-- Name: teacher_knowledge; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE IF NOT EXISTS public.teacher_knowledge (
    id bigint NOT NULL,
    class_id bigint NOT NULL,
    name character varying(100) NOT NULL,
    color character varying(20) DEFAULT '#1890ff'::character varying,
    sort_order integer DEFAULT 0 NOT NULL,
    created_by bigint NOT NULL,
    created_at timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_at timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL
);


--
-- Name: TABLE teacher_knowledge; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON TABLE public.teacher_knowledge IS '教师自定义热力图知识点';


--
-- Name: COLUMN teacher_knowledge.class_id; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.teacher_knowledge.class_id IS '班级ID';


--
-- Name: COLUMN teacher_knowledge.name; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.teacher_knowledge.name IS '知识点名称';


--
-- Name: COLUMN teacher_knowledge.color; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.teacher_knowledge.color IS '热力图颜色（十六进制）';


--
-- Name: COLUMN teacher_knowledge.sort_order; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.teacher_knowledge.sort_order IS '排序号';


--
-- Name: COLUMN teacher_knowledge.created_by; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.teacher_knowledge.created_by IS '创建教师ID';


--
-- Name: teacher_knowledge_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.teacher_knowledge_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: teacher_knowledge_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.teacher_knowledge_id_seq OWNED BY public.teacher_knowledge.id;


--
-- Name: chat_history id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.chat_history ALTER COLUMN id SET DEFAULT nextval('public.chat_history_id_seq'::regclass);


--
-- Name: class_info id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.class_info ALTER COLUMN id SET DEFAULT nextval('public.class_info_id_seq'::regclass);


--
-- Name: class_student id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.class_student ALTER COLUMN id SET DEFAULT nextval('public.class_student_id_seq'::regclass);


--
-- Name: course id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.course ALTER COLUMN id SET DEFAULT nextval('public.course_id_seq'::regclass);


--
-- Name: directory_node id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.directory_node ALTER COLUMN id SET DEFAULT nextval('public.directory_node_id_seq'::regclass);


--
-- Name: document id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.document ALTER COLUMN id SET DEFAULT nextval('public.document_id_seq'::regclass);


--
-- Name: homework_evaluation id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.homework_evaluation ALTER COLUMN id SET DEFAULT nextval('public.homework_evaluation_id_seq'::regclass);


--
-- Name: homework_task id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.homework_task ALTER COLUMN id SET DEFAULT nextval('public.homework_task_id_seq'::regclass);


--
-- Name: shared_kb id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.shared_kb ALTER COLUMN id SET DEFAULT nextval('public.shared_kb_id_seq'::regclass);


--
-- Name: shared_kb_member id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.shared_kb_member ALTER COLUMN id SET DEFAULT nextval('public.shared_kb_member_id_seq'::regclass);


--
-- Name: submission id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.submission ALTER COLUMN id SET DEFAULT nextval('public.submission_id_seq'::regclass);


--
-- Name: submission_errors id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.submission_errors ALTER COLUMN id SET DEFAULT nextval('public.submission_errors_id_seq'::regclass);


--
-- Name: sys_user id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.sys_user ALTER COLUMN id SET DEFAULT nextval('public.sys_user_id_seq'::regclass);


--
-- Name: teacher_knowledge id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.teacher_knowledge ALTER COLUMN id SET DEFAULT nextval('public.teacher_knowledge_id_seq'::regclass);


--
-- Name: chat_history chat_history_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.chat_history
    ADD CONSTRAINT chat_history_pkey PRIMARY KEY (id);


--
-- Name: class_info class_info_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.class_info
    ADD CONSTRAINT class_info_pkey PRIMARY KEY (id);


--
-- Name: class_student class_student_class_id_student_id_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.class_student
    ADD CONSTRAINT class_student_class_id_student_id_key UNIQUE (class_id, student_id);


--
-- Name: class_student class_student_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.class_student
    ADD CONSTRAINT class_student_pkey PRIMARY KEY (id);


--
-- Name: course course_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.course
    ADD CONSTRAINT course_pkey PRIMARY KEY (id);


--
-- Name: directory_node directory_node_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.directory_node
    ADD CONSTRAINT directory_node_pkey PRIMARY KEY (id);


--
-- Name: document_chunk document_chunk_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.document_chunk
    ADD CONSTRAINT document_chunk_pkey PRIMARY KEY (id);


--
-- Name: document document_doc_id_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.document
    ADD CONSTRAINT document_doc_id_key UNIQUE (doc_id);


--
-- Name: document document_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.document
    ADD CONSTRAINT document_pkey PRIMARY KEY (id);


--
-- Name: homework_evaluation homework_evaluation_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.homework_evaluation
    ADD CONSTRAINT homework_evaluation_pkey PRIMARY KEY (id);


--
-- Name: homework_task homework_task_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.homework_task
    ADD CONSTRAINT homework_task_pkey PRIMARY KEY (id);


--
-- Name: shared_kb shared_kb_invite_token_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.shared_kb
    ADD CONSTRAINT shared_kb_invite_token_key UNIQUE (invite_token);


--
-- Name: shared_kb_member shared_kb_member_kb_id_user_id_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.shared_kb_member
    ADD CONSTRAINT shared_kb_member_kb_id_user_id_key UNIQUE (kb_id, user_id);


--
-- Name: shared_kb_member shared_kb_member_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.shared_kb_member
    ADD CONSTRAINT shared_kb_member_pkey PRIMARY KEY (id);


--
-- Name: shared_kb shared_kb_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.shared_kb
    ADD CONSTRAINT shared_kb_pkey PRIMARY KEY (id);


--
-- Name: student_qq_binding student_qq_binding_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.student_qq_binding
    ADD CONSTRAINT student_qq_binding_pkey PRIMARY KEY (student_id);


--
-- Name: submission_errors submission_errors_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.submission_errors
    ADD CONSTRAINT submission_errors_pkey PRIMARY KEY (id);


--
-- Name: submission submission_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.submission
    ADD CONSTRAINT submission_pkey PRIMARY KEY (id);


--
-- Name: sys_user sys_user_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.sys_user
    ADD CONSTRAINT sys_user_pkey PRIMARY KEY (id);


--
-- Name: sys_user sys_user_username_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.sys_user
    ADD CONSTRAINT sys_user_username_key UNIQUE (username);


--
-- Name: teacher_knowledge teacher_knowledge_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.teacher_knowledge
    ADD CONSTRAINT teacher_knowledge_pkey PRIMARY KEY (id);


--
-- Name: teacher_knowledge uk_class_knowledge; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.teacher_knowledge
    ADD CONSTRAINT uk_class_knowledge UNIQUE (class_id, name);


--
-- Name: document_chunk uk_doc_chunk; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.document_chunk
    ADD CONSTRAINT uk_doc_chunk UNIQUE (doc_id, chunk_index, sub_index);


--
-- Name: class_info uq_class_invite_code; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.class_info
    ADD CONSTRAINT uq_class_invite_code UNIQUE (invite_code);


--
-- Name: idx_chat_history_created_at; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_chat_history_created_at ON public.chat_history USING btree (created_at);


--
-- Name: idx_chat_history_session_id; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_chat_history_session_id ON public.chat_history USING btree (session_id);


--
-- Name: idx_chat_history_user_id; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_chat_history_user_id ON public.chat_history USING btree (user_id);


--
-- Name: idx_chat_history_user_session; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_chat_history_user_session ON public.chat_history USING btree (user_id, session_id);


--
-- Name: idx_chunk_content_trgm; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_chunk_content_trgm ON public.document_chunk USING gin (content public.gin_trgm_ops);


--
-- Name: idx_chunk_embedding; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_chunk_embedding ON public.document_chunk USING ivfflat (embedding_vec public.vector_cosine_ops) WITH (lists='100');


--
-- Name: idx_chunk_kb; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_chunk_kb ON public.document_chunk USING btree (kb_id);


--
-- Name: idx_chunk_user; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_chunk_user ON public.document_chunk USING btree (user_id);


--
-- Name: idx_chunk_user_kb; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_chunk_user_kb ON public.document_chunk USING btree (user_id, kb_id);


--
-- Name: idx_class_info_course_id; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_class_info_course_id ON public.class_info USING btree (course_id);


--
-- Name: idx_class_info_teacher; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_class_info_teacher ON public.class_info USING btree (teacher_id);


--
-- Name: idx_class_student_class; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_class_student_class ON public.class_student USING btree (class_id);


--
-- Name: idx_class_student_student; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_class_student_student ON public.class_student USING btree (student_id);


--
-- Name: idx_course_teacher_id; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_course_teacher_id ON public.course USING btree (teacher_id);


--
-- Name: idx_dir_node_kb; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_dir_node_kb ON public.directory_node USING btree (kb_id);


--
-- Name: idx_dir_node_parent; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_dir_node_parent ON public.directory_node USING btree (parent_id);


--
-- Name: idx_dir_node_shared; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_dir_node_shared ON public.directory_node USING btree (user_id, is_shared);


--
-- Name: idx_dir_node_user; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_dir_node_user ON public.directory_node USING btree (user_id);


--
-- Name: idx_doc_kb; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_doc_kb ON public.document USING btree (kb_id);


--
-- Name: idx_document_chunk_created_at; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_document_chunk_created_at ON public.document_chunk USING btree (created_at);


--
-- Name: idx_document_chunk_doc_id; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_document_chunk_doc_id ON public.document_chunk USING btree (doc_id);


--
-- Name: idx_document_chunk_metadata_class_id; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_document_chunk_metadata_class_id ON public.document_chunk USING btree (((metadata ->> 'classId'::text)));


--
-- Name: idx_document_chunk_metadata_type; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_document_chunk_metadata_type ON public.document_chunk USING btree (((metadata ->> 'type'::text)));


--
-- Name: idx_document_user_id; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_document_user_id ON public.document USING btree (user_id);


--
-- Name: idx_embedding_hnsw; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_embedding_hnsw ON public.document_chunk USING hnsw (embedding_vec public.vector_cosine_ops) WITH (ef_construction='64');


--
-- Name: idx_eval_class_id; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_eval_class_id ON public.homework_evaluation USING btree (class_id);


--
-- Name: idx_eval_session_id; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_eval_session_id ON public.homework_evaluation USING btree (session_id);


--
-- Name: idx_eval_user_id; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_eval_user_id ON public.homework_evaluation USING btree (user_id);


--
-- Name: idx_se_class; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_se_class ON public.submission_errors USING btree (class_id);


--
-- Name: idx_se_class_kp; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_se_class_kp ON public.submission_errors USING btree (class_id, knowledge_point);


--
-- Name: idx_se_knowledge_point; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_se_knowledge_point ON public.submission_errors USING btree (knowledge_point);


--
-- Name: idx_se_submission; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_se_submission ON public.submission_errors USING btree (submission_id);


--
-- Name: idx_shared_kb_invite; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_shared_kb_invite ON public.shared_kb USING btree (invite_token);


--
-- Name: idx_shared_kb_owner; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_shared_kb_owner ON public.shared_kb USING btree (owner_id);


--
-- Name: idx_skb_member_kb; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_skb_member_kb ON public.shared_kb_member USING btree (kb_id);


--
-- Name: idx_skb_member_user; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_skb_member_user ON public.shared_kb_member USING btree (user_id);


--
-- Name: idx_student_class_no; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_student_class_no ON public.submission USING btree (student_name, class_id, assignment_no);


--
-- Name: idx_student_id; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_student_id ON public.submission USING btree (student_id);


--
-- Name: idx_submission_class_id; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_submission_class_id ON public.submission USING btree (class_id);


--
-- Name: idx_submission_class_name; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_submission_class_name ON public.submission USING btree (class_name);


--
-- Name: idx_submission_class_student; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_submission_class_student ON public.submission USING btree (class_id, student_id);


--
-- Name: idx_submission_student_name; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_submission_student_name ON public.submission USING btree (student_name);


--
-- Name: idx_submission_student_task_time; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_submission_student_task_time ON public.submission USING btree (student_id, task_id, submitted_at DESC);


--
-- Name: idx_submission_task_student; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_submission_task_student ON public.submission USING btree (task_id, student_id);


--
-- Name: idx_sys_user_class_status; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_sys_user_class_status ON public.sys_user USING btree (class_id, status);


--
-- Name: idx_task_class_id; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_task_class_id ON public.homework_task USING btree (class_id);


--
-- Name: idx_task_status_deadline; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_task_status_deadline ON public.homework_task USING btree (status, deadline);


--
-- Name: idx_teacher_knowledge_class; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_teacher_knowledge_class ON public.teacher_knowledge USING btree (class_id);


--
-- Name: chat_history update_chat_history_updated_at; Type: TRIGGER; Schema: public; Owner: -
--

CREATE TRIGGER update_chat_history_updated_at BEFORE UPDATE ON public.chat_history FOR EACH ROW EXECUTE FUNCTION public.update_updated_at_column();


--
-- Name: course update_course_updated_at; Type: TRIGGER; Schema: public; Owner: -
--

CREATE TRIGGER update_course_updated_at BEFORE UPDATE ON public.course FOR EACH ROW EXECUTE FUNCTION public.update_updated_at_column();


--
-- Name: directory_node update_directory_node_updated_at; Type: TRIGGER; Schema: public; Owner: -
--

CREATE TRIGGER update_directory_node_updated_at BEFORE UPDATE ON public.directory_node FOR EACH ROW EXECUTE FUNCTION public.update_updated_at_column();


--
-- Name: document update_document_updated_at; Type: TRIGGER; Schema: public; Owner: -
--

CREATE TRIGGER update_document_updated_at BEFORE UPDATE ON public.document FOR EACH ROW EXECUTE FUNCTION public.update_updated_at_column();


--
-- Name: shared_kb update_shared_kb_updated_at; Type: TRIGGER; Schema: public; Owner: -
--

CREATE TRIGGER update_shared_kb_updated_at BEFORE UPDATE ON public.shared_kb FOR EACH ROW EXECUTE FUNCTION public.update_updated_at_column();


--
-- Name: submission_errors fk_se_class; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.submission_errors
    ADD CONSTRAINT fk_se_class FOREIGN KEY (class_id) REFERENCES public.class_info(id) ON DELETE CASCADE;


--
-- Name: submission_errors fk_se_submission; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.submission_errors
    ADD CONSTRAINT fk_se_submission FOREIGN KEY (submission_id) REFERENCES public.submission(id) ON DELETE CASCADE;


--
-- Name: submission fk_submission_class; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.submission
    ADD CONSTRAINT fk_submission_class FOREIGN KEY (class_id) REFERENCES public.class_info(id);


--
-- Name: homework_task fk_task_class; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.homework_task
    ADD CONSTRAINT fk_task_class FOREIGN KEY (class_id) REFERENCES public.class_info(id);


--
-- Name: teacher_knowledge fk_tk_class; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.teacher_knowledge
    ADD CONSTRAINT fk_tk_class FOREIGN KEY (class_id) REFERENCES public.class_info(id) ON DELETE CASCADE;


--
-- Name: homework_evaluation fk_user; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.homework_evaluation
    ADD CONSTRAINT fk_user FOREIGN KEY (user_id) REFERENCES public.sys_user(id);


--
-- Name: shared_kb_member shared_kb_member_kb_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.shared_kb_member
    ADD CONSTRAINT shared_kb_member_kb_id_fkey FOREIGN KEY (kb_id) REFERENCES public.shared_kb(id) ON DELETE CASCADE;


-- Flyway init schema — 完整数据库初始结构

