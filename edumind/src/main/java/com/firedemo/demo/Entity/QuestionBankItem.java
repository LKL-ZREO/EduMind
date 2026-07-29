package com.firedemo.demo.Entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("question_bank_item")
public class QuestionBankItem {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long teacherId;

    private String title;

    private String requirement;

    /** CHOICE / OPEN / EXERCISE / HOMEWORK */
    private String type;

    /** JSONB 选项快照，非选择题为 null。 */
    private String options;

    private String correctKey;

    private String explanation;

    private String knowledgePoint;

    private String difficulty;

    private Integer defaultTimeLimit;

    private Integer score;

    private Boolean uploadRequired;

    private String sourceDocId;

    private Boolean aiGenerated;

    private Boolean archived;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
