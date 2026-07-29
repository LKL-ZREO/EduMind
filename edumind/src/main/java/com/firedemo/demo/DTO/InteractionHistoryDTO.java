package com.firedemo.demo.DTO;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.util.List;

@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class InteractionHistoryDTO {
    private Long interactionId;
    private Long questionId;
    private String type;
    private String title;
    private String description;
    private List<InteractionCreateDTO.OptionDTO> options;
    private String correctKey;
    private Integer timeLimit;
    private String status;
    private String knowledgePoint;
    private String difficulty;
    private String createdAt;
    // 统计摘要（教师端用）
    private Integer totalStudents;
    private Integer respondedCount;
    private Double correctRate;
    // 学生自己的作答（学生端用）
    private String myAnswer;
    private Boolean myCorrect;
}
