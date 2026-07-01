package com.firedemo.demo.DTO;

import lombok.Data;

/**
 * 班级信息DTO
 */
@Data
public class ClassInfoDTO {

    private Long id;
    private String name;
    private Integer studentCount;
}
