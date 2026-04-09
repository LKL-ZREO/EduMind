package com.firedemo.demo.Entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 文档块数据库实体
 */
@Data
@TableName("document_chunk")
public class DocumentChunkEntity {
    
    @TableId(type = IdType.INPUT)
    private String id;
    
    /**
     * 块内容
     */
    private String content;
    
    /**
     * 嵌入向量（逗号分隔的字符串）
     */
    private String embedding;
    
    /**
     * 所属文档ID
     */
    private String documentId;
    
    /**
     * 文档名称
     */
    private String documentName;
    
    /**
     * 章节索引
     */
    private Integer sectionIndex;
    
    /**
     * 子索引
     */
    private Integer subIndex;
    
    /**
     * Token数量
     */
    private Integer tokenCount;
    
    /**
     * 字符数
     */
    private Integer charCount;
    
    /**
     * 前文摘要
     */
    private String prevSummary;
    
    /**
     * 后文摘要
     */
    private String nextSummary;
    
    /**
     * 创建时间
     */
    private LocalDateTime createdAt;
}
