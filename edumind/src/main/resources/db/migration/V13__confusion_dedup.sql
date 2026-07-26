-- V13: 不懂标记去重 — 清理已有重复数据 + 添加唯一约束防止后续重复
-- 同一学生对同一题目只能标记一次不懂

-- 清理已有重复（保留最早的记录）
DELETE FROM live_confusion_event a
USING live_confusion_event b
WHERE a.id > b.id
  AND a.interaction_id IS NOT NULL
  AND a.interaction_id = b.interaction_id
  AND a.student_id = b.student_id;

-- 添加唯一约束
ALTER TABLE live_confusion_event
    ADD CONSTRAINT uq_confusion_interaction_student UNIQUE (interaction_id, student_id);
