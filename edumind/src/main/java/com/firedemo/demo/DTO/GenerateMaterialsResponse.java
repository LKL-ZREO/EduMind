package com.firedemo.demo.DTO;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class GenerateMaterialsResponse {
    private PreviewItem preview;
    private List<QuizItem> quizzes;
    private String pptFileName;
    /** 预习作业部分失败时的错误信息（null 表示成功） */
    private String previewError;
    /** 课堂试题部分失败时的错误信息（null 表示成功） */
    private String quizError;

    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    public static class PreviewItem {
        @NotBlank @Size(max = 500)
        private String topic;
        @NotBlank @Size(max = 20_000)
        private String guideText;
        @Valid @Size(min = 1, max = 20)
        private List<QuestionItem> questions;
        @NotBlank @Size(max = 2_000)
        private String discussionQuestion;
        private Long savedId;
        private boolean published;
    }

    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    public static class QuizItem {
        @NotBlank @Pattern(regexp = "CHOICE|OPEN|EXERCISE")
        private String type;
        @NotBlank @Size(max = 4_000)
        private String title;
        @Valid @Size(max = 10)
        private List<OptionItem> options;
        @NotBlank @Size(max = 4_000)
        private String correctKey;
        @NotBlank @Size(max = 500)
        private String knowledgePoint;
        @NotBlank @Pattern(regexp = "easy|medium|hard")
        private String difficulty;
        @NotNull @Min(1) @Max(1800)
        private Integer timeLimit;
        private Long savedId;
        private boolean published;
    }

    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    public static class QuestionItem {
        @NotBlank @Pattern(regexp = "CHOICE|OPEN|EXERCISE")
        private String type;
        @NotBlank @Size(max = 4_000)
        private String question;
        @Valid @Size(max = 10)
        private List<OptionItem> options;
        @NotBlank @Size(max = 4_000)
        private String correctKey;
    }

    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    public static class OptionItem {
        @NotBlank @Size(max = 10)
        private String key;
        @NotBlank @Size(max = 1_000)
        private String text;
    }
}
