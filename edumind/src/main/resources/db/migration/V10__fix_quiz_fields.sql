-- V10: 修复草稿题字段限制
ALTER TABLE public.interaction ALTER COLUMN correct_key TYPE TEXT;
