package com.firedemo.demo.DTO;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

/** 创建或编辑统一题目。字段为可选以支持 PATCH，创建时由服务层校验题干。 */
@Data
public class QuestionUpsertDTO {
    @Pattern(regexp = "CHOICE|OPEN|EXERCISE|HOMEWORK")
    private String type;

    @Size(max = 4000)
    private String title;

    @Size(max = 20_000)
    private String requirement;

    @Valid
    @Size(max = 10)
    private List<OptionDTO> options;

    @Size(max = 4000)
    private String correctKey;

    @Size(max = 20_000)
    private String explanation;

    @Size(max = 500)
    private String knowledgePoint;

    @Pattern(regexp = "easy|medium|hard")
    private String difficulty;

    @Min(1)
    @Max(1800)
    private Integer timeLimit;

    @Min(0)
    private Integer score;

    private Boolean uploadRequired;

    @Size(max = 64)
    private String sourceDocId;

    private Boolean aiGenerated;

    @Data
    public static class OptionDTO {
        @Size(min = 1, max = 10)
        private String key;

        @Size(min = 1, max = 1000)
        private String text;
    }
}
