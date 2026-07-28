package com.firedemo.demo.DTO;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * 作业评价结果DTO（OpenClaw返回的JSON格式）
 */
@Data
public class EvaluationResultDTO {

    @JsonProperty("totalScore")
    @Min(0) @Max(100)
    private Integer totalScore;

    @JsonProperty("contentScore")
    @Min(0) @Max(100)
    private Integer contentScore;

    @JsonProperty("formatScore")
    @Min(0) @Max(100)
    private Integer formatScore;

    @JsonProperty("maxScore")
    @Min(1) @Max(100)
    private Integer maxScore;

    @JsonProperty("grade")
    private String grade;

    @JsonProperty("overallComment")
    private String overallComment;

    @JsonProperty("strengths")
    @Size(max = 20)
    private List<String> strengths;

    @JsonProperty("weaknesses")
    @Size(max = 20)
    private List<String> weaknesses;

    @JsonProperty("suggestions")
    @Valid @Size(max = 20)
    private List<SuggestionItem> suggestions;

    @JsonProperty("errors")
    @Valid @Size(max = 50)
    private List<ErrorItem> errors;

    @JsonProperty("knowledgePoints")
    @Valid @Size(max = 30)
    private List<KnowledgePointItem> knowledgePoints;

    @JsonProperty("scoringDetails")
    @Valid @Size(max = 20)
    private List<ScoringDetailItem> scoringDetails;

    /**
     * 建议项
     */
    @Data
    public static class SuggestionItem {
        private String priority;
        private String issue;
        private String suggestion;
        /** 归属知识点（SKILL 返回字段） */
        private String knowledgePoint;
    }

    /**
     * 错误项
     */
    @Data
    public static class ErrorItem {
        private Integer line;
        private String type;
        private String issue;
        private String severity;
        /** 归属知识点（SKILL 返回字段） */
        private String knowledgePoint;
    }

    /**
     * 知识点项
     */
    @Data
    public static class KnowledgePointItem {
        private String name;
        @Min(0) @Max(100)
        private Integer mastery;
        private String status;
    }

    /**
     * 评分详情项
     */
    @Data
    public static class ScoringDetailItem {
        private String dimension;
        @Min(0) @Max(100)
        private Integer score;
        @Min(0) @Max(100)
        private Integer maxScore;
        private String comment;
    }
}
