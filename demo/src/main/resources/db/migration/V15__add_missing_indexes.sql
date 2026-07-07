-- ============================================================
-- V15: 为高频查询添加缺失索引
-- ============================================================

-- 教师查询班级列表（ClassInfoMapper.selectByTeacherId）
CREATE INDEX IF NOT EXISTS idx_class_info_teacher ON class_info(teacher_id);

-- 查询班级学生（UserMapper.selectStudentsByClassId / countStudentsByClassId）
CREATE INDEX IF NOT EXISTS idx_sys_user_class_status ON sys_user(class_id, status);

-- 按班级查询作业评价（HomeworkEvaluationMapper 6 个方法使用）
CREATE INDEX IF NOT EXISTS idx_eval_class_id ON homework_evaluation(class_id);

-- 按班级查询作业任务（HomeworkTaskMapper.selectByClassId）
CREATE INDEX IF NOT EXISTS idx_task_class_id ON homework_task(class_id);

-- 查询进行中的作业任务（HomeworkTaskMapper.selectActiveWithDeadline）
CREATE INDEX IF NOT EXISTS idx_task_status_deadline ON homework_task(status, deadline);

-- 按学生+作业去重查询提交（反连接 + countByStudentIdAndTaskId）
CREATE INDEX IF NOT EXISTS idx_submission_task_student ON submission(task_id, student_id);

-- 每学生每作业取最新提交的高频子查询模式
CREATE INDEX IF NOT EXISTS idx_submission_student_task_time
    ON submission(student_id, task_id, submitted_at DESC);

-- 班级+学号联合查询（StudentStatsTool 等）
CREATE INDEX IF NOT EXISTS idx_submission_class_student ON submission(class_id, student_id);

-- 用户文档列表（DocumentMapper.selectByUserId）
CREATE INDEX IF NOT EXISTS idx_document_user_id ON document(user_id);

-- 对话历史按用户+会话分组（selectSessionIdsByUserId）
CREATE INDEX IF NOT EXISTS idx_chat_history_user_session ON chat_history(user_id, session_id);
