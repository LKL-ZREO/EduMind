package com.firedemo.demo.DTO;

import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 编辑班级请求
 */
@Data
public class UpdateClassDTO {

    @Size(max = 30)
    private String name;

    @Size(max = 64)
    private String courseGroup;

    @Size(max = 200)
    private String description;
}
