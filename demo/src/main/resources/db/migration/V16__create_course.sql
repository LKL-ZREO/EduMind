-- ============================================
-- V16: 课程表 + 班级关联课程
-- ============================================

-- 1. 课程表
CREATE TABLE IF NOT EXISTS course (
    id              BIGSERIAL PRIMARY KEY,
    name            VARCHAR(100)  NOT NULL,
    system_prompt   TEXT          NOT NULL,
    knowledge_scope TEXT,
    teacher_id      BIGINT,
    created_at      TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- 2. 班级关联课程
ALTER TABLE class_info ADD COLUMN IF NOT EXISTS course_id BIGINT;

-- 3. 触发器
DROP TRIGGER IF EXISTS update_course_updated_at ON course;
CREATE TRIGGER update_course_updated_at BEFORE UPDATE ON course
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

-- 4. 索引
CREATE INDEX IF NOT EXISTS idx_class_info_course_id ON class_info(course_id);
CREATE INDEX IF NOT EXISTS idx_course_teacher_id ON course(teacher_id);

-- 5. 种子数据：将现有的 course_group 映射到课程
INSERT INTO course (name, system_prompt, teacher_id)
SELECT DISTINCT
    COALESCE(course_group, 'C语言程序设计'),
    '你是教学助手，服务于' || COALESCE(course_group, 'C语言程序设计') || '课程。请严格遵循以下工具调用规则：

1. 【必须检索知识库】涉及课程专业知识时，先调用 searchKnowledge
2. 【必须查实时数据】班级/学生问题调用对应工具：
   - 班级整体情况 → queryClassStatus
   - 单个学生成绩 → queryStudentStats
   - 作业任务列表 → queryHomeworkTasks
3. 【无需工具】打招呼、闲聊、感谢、简单追问
关键原则：宁可多搜一次，不要凭记忆硬答。',
    teacher_id
FROM class_info
WHERE course_group IS NOT NULL AND course_group != ''
  AND NOT EXISTS (SELECT 1 FROM course WHERE course.name = class_info.course_group);

-- 将现有班级关联到课程
UPDATE class_info ci
SET course_id = c.id
FROM course c
WHERE ci.course_id IS NULL
  AND ci.course_group IS NOT NULL
  AND c.name = ci.course_group;
