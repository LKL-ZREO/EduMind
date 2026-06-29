package com.firedemo.demo.DTO;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 创建班级请求
 */
@Data
public class CreateClassDTO {

    @NotBlank(message = "班级名称不能为空")
    @Size(max = 30)
    private String name;

    @Size(max = 64)
    private String courseGroup;

    @Size(max = 200)
    private String description;

    /** QQ群号 */
    private String qqGroupId;

    /** 课程ID */
    private Long courseId;
}
