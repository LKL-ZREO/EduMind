package com.firedemo.demo.DTO;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 知识点掌握度DTO
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class KnowledgeMasteryDTO {

    private Long id;
    private String name;
    /** 掌握度 0-100（按错误率换算） */
    private Integer mastery;
    /** 错误总数 */
    private Integer errorCount;
    /** 严重错误数 */
    private Integer criticalCount;
    /** 热力图颜色 */
    private String color;
}
