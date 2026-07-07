package com.firedemo.demo.DTO;

import lombok.Data;
import java.util.List;

/**
 * 批量保存教师知识点请求
 */
@Data
public class TeacherKnowledgeSaveRequest {

    private Long classId;
    private List<TeacherKnowledgeDTO> items;
}
