-- V5__fix_document_chunk_columns.sql
-- 修复 document_chunk 表字段名，与 Entity 保持一致

-- 如果字段不存在则添加
DO $$
BEGIN
    -- doc_id
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns 
                   WHERE table_name='document_chunk' AND column_name='doc_id') THEN
        ALTER TABLE document_chunk ADD COLUMN doc_id VARCHAR(64);
    END IF;
    
    -- doc_name
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns 
                   WHERE table_name='document_chunk' AND column_name='doc_name') THEN
        ALTER TABLE document_chunk ADD COLUMN doc_name VARCHAR(255);
    END IF;
    
    -- chunk_index
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns 
                   WHERE table_name='document_chunk' AND column_name='chunk_index') THEN
        ALTER TABLE document_chunk ADD COLUMN chunk_index INT DEFAULT 0;
    END IF;
    
    -- sub_index
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns 
                   WHERE table_name='document_chunk' AND column_name='sub_index') THEN
        ALTER TABLE document_chunk ADD COLUMN sub_index INT DEFAULT 0;
    END IF;
    
    -- prev_summary
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns 
                   WHERE table_name='document_chunk' AND column_name='prev_summary') THEN
        ALTER TABLE document_chunk ADD COLUMN prev_summary TEXT;
    END IF;
    
    -- next_summary
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns 
                   WHERE table_name='document_chunk' AND column_name='next_summary') THEN
        ALTER TABLE document_chunk ADD COLUMN next_summary TEXT;
    END IF;
END $$;

-- 删除旧的不需要的字段（如果存在）
DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM information_schema.columns 
               WHERE table_name='document_chunk' AND column_name='document_id') THEN
        ALTER TABLE document_chunk DROP COLUMN document_id;
    END IF;
END $$;

-- 更新唯一约束
ALTER TABLE document_chunk DROP CONSTRAINT IF EXISTS uk_doc_chunk;
ALTER TABLE document_chunk ADD CONSTRAINT uk_doc_chunk UNIQUE (doc_id, chunk_index, sub_index);
