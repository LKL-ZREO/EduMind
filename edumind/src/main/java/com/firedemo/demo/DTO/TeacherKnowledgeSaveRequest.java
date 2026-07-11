package com.firedemo.demo.DTO;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.List;

/**
 * 批量保存教师知识点请求
 */
@Data
public class TeacherKnowledgeSaveRequest {

    @NotNull(message = "班级ID不能为空")
    private Long classId;

    @NotEmpty(message = "知识点列表不能为空")
    private List<TeacherKnowledgeDTO> items;
}
