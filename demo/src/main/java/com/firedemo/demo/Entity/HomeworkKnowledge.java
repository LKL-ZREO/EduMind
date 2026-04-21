package com.firedemo.demo.Entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 作业知识点掌握情况实体
 */
@Data
@TableName("homework_knowledge")
public class HomeworkKnowledge {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long evaluationId;

    private String knowledgePoint;

    private Integer mastery;

    private String status;

    private LocalDateTime createdAt;
}
