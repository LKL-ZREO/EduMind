-- ============================================================
-- 班级管理功能 - 数据库迁移脚本
-- 执行环境：PostgreSQL
-- 日期：2026-06-01
-- 说明：新增课程分组、邀请码、归档状态等字段，兼容已有数据
-- ============================================================

BEGIN;

-- ============================================================
-- 1. class_info 表新增字段
-- ============================================================

-- 1.1 所属课程（无约束，DEFAULT ''）
ALTER TABLE class_info ADD COLUMN IF NOT EXISTS course_group VARCHAR(64) DEFAULT '';

-- 1.2 状态（ACTIVE / ARCHIVED）
ALTER TABLE class_info ADD COLUMN IF NOT EXISTS status VARCHAR(20) DEFAULT 'ACTIVE';

-- 1.3 更新时间
ALTER TABLE class_info ADD COLUMN IF NOT EXISTS updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP;

-- 1.4 邀请码（先加字段不加约束，补数据后再加约束）
ALTER TABLE class_info ADD COLUMN IF NOT EXISTS invite_code VARCHAR(8);

-- ============================================================
-- 2. 为已有数据生成唯一邀请码
-- ============================================================
DO $$
DECLARE
    r RECORD;
    v_code VARCHAR(8);
    v_tries INT;
    v_max_tries INT := 10;
    v_chars TEXT := 'ABCDEFGHJKLMNPQRSTUVWXYZ23456789'; -- 排除 0/O/1/I/L 防混淆
BEGIN
    FOR r IN SELECT id FROM class_info WHERE invite_code IS NULL OR invite_code = '' LOOP
        v_tries := 0;
        LOOP
            -- 生成 6 位随机码
            v_code := '';
            FOR i IN 1..6 LOOP
                v_code := v_code || substr(v_chars, floor(random() * length(v_chars))::int + 1, 1);
            END LOOP;

            -- 查重
            PERFORM 1 FROM class_info WHERE invite_code = v_code AND id != r.id;
            IF NOT FOUND THEN
                EXIT;
            END IF;

            v_tries := v_tries + 1;
            IF v_tries >= v_max_tries THEN
                RAISE EXCEPTION '无法为班级 ID=% 生成唯一邀请码（已重试 % 次）', r.id, v_max_tries;
            END IF;
        END LOOP;

        UPDATE class_info SET invite_code = v_code WHERE id = r.id;
    END LOOP;
END $$;

-- ============================================================
-- 3. 加约束
-- ============================================================
ALTER TABLE class_info ALTER COLUMN invite_code SET NOT NULL;
DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'uq_class_invite_code') THEN
        ALTER TABLE class_info ADD CONSTRAINT uq_class_invite_code UNIQUE (invite_code);
    END IF;
END $$;

-- ============================================================
-- 4. class_student 表（如果不存在则创建）
-- ============================================================
DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_tables WHERE tablename = 'class_student') THEN
        CREATE TABLE class_student (
            id            BIGSERIAL PRIMARY KEY,
            class_id      BIGINT NOT NULL,
            student_id    VARCHAR(64) NOT NULL,
            student_name  VARCHAR(64),
            source        VARCHAR(20) DEFAULT 'manual',
            created_at    TIMESTAMP DEFAULT NOW(),
            CONSTRAINT uq_class_student UNIQUE (class_id, student_id)
        );
        CREATE INDEX idx_cs_class_id ON class_student(class_id);
        CREATE INDEX idx_cs_student_id ON class_student(student_id);
    END IF;
END $$;

COMMIT;
