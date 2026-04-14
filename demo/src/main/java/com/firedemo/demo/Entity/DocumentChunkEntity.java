package com.firedemo.demo.Entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 文档块数据库实体 - 与 document_chunk 表对应
 */
@Data
@TableName("document_chunk")
public class DocumentChunkEntity {
    
    @TableId(type = IdType.INPUT)
    private String id;
    
    /**
     * 所属文档ID (对应 doc_id 字段)
     */
    @TableField("doc_id")
    private String documentId;
    
    /**
     * 文档名称 (对应 doc_name 字段)
     */
    @TableField("doc_name")
    private String documentName;
    
    /**
     * 块序号 (对应 chunk_index 字段)
     */
    @TableField("chunk_index")
    private Integer sectionIndex;
    
    /**
     * 子块序号 (对应 sub_index 字段)
     */
    @TableField("sub_index")
    private Integer subIndex;
    
    /**
     * 块内容
     */
    private String content;
    
    /**
     * Token数量
     */
    private Integer tokenCount;
    
    /**
     * 字符数
     */
    private Integer charCount;
    
    /**
     * 嵌入向量（逗号分隔的字符串）
     */
    private String embedding;
    
    /**
     * 前一块摘要 (对应 prev_summary 字段)
     */
    private String prevSummary;
    
    /**
     * 后一块摘要 (对应 next_summary 字段)
     */
    private String nextSummary;
    
    /**
     * 创建时间
     */
    private LocalDateTime createdAt;
}
