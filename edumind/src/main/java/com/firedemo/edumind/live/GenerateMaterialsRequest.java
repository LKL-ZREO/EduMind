package com.firedemo.edumind.live;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class GenerateMaterialsRequest {
    private Long classId;  // 可选，生成时可不选，发布时再指定
    @NotBlank(message = "请选择PPT文件")
    private String docId;
    private Long teacherId;  // 由 Controller 注入
}
