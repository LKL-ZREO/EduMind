package com.firedemo.demo.DTO;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

/** 统一题库的稳定输出模型，供知识库、作业和实时课堂共同使用。 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class QuestionDTO {
    private Long id;
    private String type;
    private String title;
    private String requirement;
    private List<OptionDTO> options;
    private String correctKey;
    private String explanation;
    private String knowledgePoint;
    private String difficulty;
    private Integer timeLimit;
    private Integer score;
    private Boolean uploadRequired;
    private String sourceDocId;
    private Boolean aiGenerated;
    private Boolean archived;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class OptionDTO {
        private String key;
        private String text;
    }
}
