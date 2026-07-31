package com.firedemo.edumind.homework;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("homework_draft_question")
public class HomeworkDraftQuestion {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long draftId;

    private Long questionId;

    private Integer sortOrder;

    private LocalDateTime createdAt;
}
