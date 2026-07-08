package com.firedemo.demo.Entity;

import lombok.Data;
import java.time.LocalDateTime;
import java.util.Map;

/**
 * 文档块实体
 */
@Data
public class DocumentChunk {
    
    private String id;
    
    /**
     * 块内容
     */
    private String content;
    
    /**
     * 嵌入向量
     */
    private float[] embedding;
    
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
    private int sectionIndex;
    
    /**
     * 子索引（如果一个章节被分割）
     */
    private int subIndex;
    
    /**
     * 总章节数
     */
    private int totalSections;
    
    /**
     * Token数量
     */
    private int tokenCount;
    
    /**
     * 字符数
     */
    private int charCount;
    
    /**
     * 前文摘要（用于上下文恢复）
     */
    private String prevSummary;
    
    /**
     * 后文摘要
     */
    private String nextSummary;
    
    /**
     * 上传者ID（私人知识库隔离用）
     */
    private Long userId;

    /**
     * 所属共享知识库ID，NULL=私人文档
     */
    private Long kbId;

    /**
     * 额外元数据
     */
    private Map<String, Object> metadata;
    
    /**
     * 创建时间
     */
    private LocalDateTime createdAt;
    
    public DocumentChunk() {
        this.createdAt = LocalDateTime.now();
    }
    
    /**
     * 获取完整索引标识
     */
    public String getFullIndex() {
        return sectionIndex + "-" + subIndex;
    }
    
    /**
     * 是否是章节开头
     */
    public boolean isSectionStart() {
        return subIndex == 0;
    }
    
    /**
     * 获取带上下文的完整内容
     */
    public String getContextualContent() {
        StringBuilder sb = new StringBuilder();
        if (prevSummary != null && !prevSummary.isEmpty()) {
            sb.append("[前文摘要] ").append(prevSummary).append("\n\n");
        }
        sb.append(content);
        if (nextSummary != null && !nextSummary.isEmpty()) {
            sb.append("\n\n[后文摘要] ").append(nextSummary);
        }
        return sb.toString();
    }
}
