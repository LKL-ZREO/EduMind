package com.firedemo.edumind.teaching;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * AI 备课建议缓存 — 按班级存储 LLM 生成的备课建议
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("ai_suggestion_cache")
public class AiSuggestionCache {
    @TableId(type = IdType.INPUT)
    private Long classId;

    private String suggestion;

    private LocalDateTime updatedAt;
}
