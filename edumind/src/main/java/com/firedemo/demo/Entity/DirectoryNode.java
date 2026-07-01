package com.firedemo.demo.Entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 目录节点实体
 * 支持无限层级嵌套的知识库目录树
 *
 * @author 海克斯
 */
@Data
@TableName("directory_node")
public class DirectoryNode {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 所属用户ID */
    private Long userId;

    /** 父节点ID，null 表示根节点 */
    private Long parentId;

    /** 显示名称 */
    private String label;

    /** 节点类型：folder | file */
    private String nodeType;

    /** 关联文档ID（file 类型时非空） */
    private String docId;

    /** 同级排序 */
    private Integer sortOrder;

    /** 是否共享给其他用户 */
    @TableField("is_shared")
    private Boolean isShared;

    /** 所属共享知识库ID */
    private Long kbId;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
