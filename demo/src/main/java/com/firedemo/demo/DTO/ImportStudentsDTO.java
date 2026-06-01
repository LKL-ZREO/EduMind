package com.firedemo.demo.DTO;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import lombok.Data;
import java.util.List;

/**
 * 批量导入学生请求
 */
@Data
public class ImportStudentsDTO {

    @NotEmpty(message = "学生列表不能为空")
    @Size(max = 200, message = "单次最多导入200名学生")
    private List<StudentItem> students;

    @Data
    public static class StudentItem {
        private String studentId;
        private String studentName;
    }
}
