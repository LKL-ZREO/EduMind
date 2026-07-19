package com.firedemo.demo.DTO;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class GenerateMaterialsRequest {
    @NotNull(message = "请选择班级")
    private Long classId;
    @NotBlank(message = "请选择PPT文件")
    private String docId;
    private Long teacherId;  // 由 Controller 注入
}
