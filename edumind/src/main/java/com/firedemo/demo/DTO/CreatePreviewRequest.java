package com.firedemo.demo.DTO;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class CreatePreviewRequest {
    @NotNull(message = "请选择班级")
    private Long classId;

    @NotBlank(message = "请输入知识点")
    private String knowledgePoint;

    private String topic;  // 可选，自定义主题
    private String docId;  // 可选，关联的来源文档ID（如PPT）
}
