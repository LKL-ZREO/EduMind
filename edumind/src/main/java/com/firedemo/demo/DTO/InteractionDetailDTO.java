package com.firedemo.demo.DTO;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class InteractionDetailDTO {
    // 题目信息
    private Long interactionId;
    private Long questionId;
    private String type;
    private String title;
    private String description;
    private List<InteractionCreateDTO.OptionDTO> options;
    private String correctKey;
    private Integer timeLimit;
    private String status;
    private String difficulty;
    private String explanation;
    // 统计
    private int totalStudents;
    private int respondedCount;
    private Map<String, DistributionItem> distribution;
    private Double correctRate;
    private List<String> unrespondedStudents;
    // 学生作答明细
    private List<ResponseItem> responses;

    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    public static class DistributionItem {
        private int count;
        private double percent;
    }

    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    public static class ResponseItem {
        private String studentId;
        private String studentName;
        private String answer;
        private Boolean isCorrect;
        private LocalDateTime respondedAt;
    }
}
