package com.firedemo.demo.DTO;

import lombok.Data;

import java.util.List;

/**
 * 教案生成请求DTO
 */
@Data
public class TeachingPlanRequestDTO {

    private Long classId;
    private List<String> goals;
    private String planType;
    private List<String> weakKnowledgePoints;
}
