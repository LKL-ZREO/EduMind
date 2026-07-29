package com.firedemo.demo.DTO;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class QuestionBoardItemDTO {
    private Long questionId;
    private Long interactionId;
    private String type;
    private String title;
    private String description;
    private List<InteractionCreateDTO.OptionDTO> options;
    private String correctKey;
    private String knowledgePoint;
    private String difficulty;
    private String status;
    private Integer sortOrder;
    private Integer timeLimit;
    private Integer sendCount;
    private String createdAt;
    private String activatedAt;
    private Long deadlineEpochMs;
    private Integer totalStudents;
    private Integer respondedCount;
    private Double correctRate;
    private Map<String, LiveStatsDTO.DistributionItem> distribution;
}
