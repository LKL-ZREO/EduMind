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

    private Integer score;

    private Boolean uploadRequired;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
