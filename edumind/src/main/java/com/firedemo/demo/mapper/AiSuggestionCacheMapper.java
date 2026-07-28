package com.firedemo.demo.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.firedemo.demo.Entity.AiSuggestionCache;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface AiSuggestionCacheMapper extends BaseMapper<AiSuggestionCache> {

    /** Upsert：插入或更新 AI 备课建议缓存 */
    @Insert("""
        INSERT INTO ai_suggestion_cache (class_id, suggestion, updated_at)
        VALUES (#{cache.classId}, #{cache.suggestion}, NOW())
        ON CONFLICT (class_id) DO UPDATE SET
            suggestion = EXCLUDED.suggestion,
            updated_at = NOW()
    """)
    void upsert(@Param("cache") AiSuggestionCache cache);
}
