package com.firedemo.demo.DTO;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.util.List;

/**
 * 作业评价结果DTO（OpenClaw返回的JSON格式）
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class EvaluationResultDTO {

    @JsonProperty("totalScore")
    private Integer totalScore;

    @JsonProperty("contentScore")
    private Integer contentScore;

    @JsonProperty("formatScore")
    private Integer formatScore;

    @JsonProperty("maxScore")
    private Integer maxScore;

    @JsonProperty("grade")
    private String grade;

    @JsonProperty("overallComment")
    private String overallComment;

    @JsonProperty("strengths")
    private List<String> strengths;

    @JsonProperty("weaknesses")
    private List<String> weaknesses;

    @JsonProperty("suggestions")
    private List<SuggestionItem> suggestions;

    @JsonProperty("errors")
    private List<ErrorItem> errors;

    @JsonProperty("knowledgePoints")
    private List<KnowledgePointItem> knowledgePoints;

    @JsonProperty("scoringDetails")
    private List<ScoringDetailItem> scoringDetails;

    /**
     * 建议项
     */
    @Data
    public static class SuggestionItem {
        private String priority;
        private String issue;
        private String suggestion;
    }

    /**
     * 错误项
     */
    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class ErrorItem {
        private Integer line;
        private String type;
        private String issue;
        private String severity;
    }

    /**
     * 知识点项
     */
    @Data
    public static class KnowledgePointItem {
        private String name;
        private Integer mastery;
        private String status;
    }

    /**
     * 评分详情项
     */
    @Data
    public static class ScoringDetailItem {
        private String dimension;
        private Integer score;
        private Integer maxScore;
        private String comment;
    }
}
