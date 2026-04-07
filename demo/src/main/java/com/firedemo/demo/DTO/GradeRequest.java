package com.firedemo.demo.DTO;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;



/**
 * 作业批改请求DTO
 *
 * @author firedemo
 * @since 2026-03-28
 */
@Data
public class GradeRequest {

    /**
     * 文件路径
     */
    @NotBlank(message = "文件路径不能为空")
    private String filePath;

    /**
     * 批改要求
     */
    @NotBlank(message = "批改要求不能为空")
    private String requirement;
}
