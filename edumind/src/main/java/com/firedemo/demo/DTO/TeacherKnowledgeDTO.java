package com.firedemo.demo.DTO;

import lombok.Data;

/**
 * 教师自定义知识点 DTO（前后端交互用）
 */
@Data
public class TeacherKnowledgeDTO {

    private Long id;
    private String name;
    private String color;
    private Integer sortOrder;
}
