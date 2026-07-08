package com.firedemo.demo.DTO;

import lombok.Data;

/**
 * 添加单个知识点请求
 */
@Data
public class TeacherKnowledgeAddRequest {
    private Long classId;
    private String name;
    private String color;
}
