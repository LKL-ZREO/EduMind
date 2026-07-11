package com.firedemo.demo.DTO;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 添加单个知识点请求
 */
@Data
public class TeacherKnowledgeAddRequest {

    @NotNull(message = "班级ID不能为空")
    private Long classId;

    @NotBlank(message = "知识点名称不能为空")
    private String name;

    private String color;
}
