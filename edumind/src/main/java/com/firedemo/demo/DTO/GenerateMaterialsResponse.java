package com.firedemo.demo.DTO;

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
        private String topic;
        private String guideText;
        private List<QuestionItem> questions;
        private String discussionQuestion;
        private Long savedId;
        private boolean published;
    }

    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    public static class QuizItem {
        private String type;
        private String title;
        private List<OptionItem> options;
        private String correctKey;
        private String knowledgePoint;
        private String difficulty;
        private Integer timeLimit;
        private Long savedId;
        private boolean published;
    }

    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    public static class QuestionItem {
        private String type;
        private String question;
        private List<OptionItem> options;
        private String correctKey;
    }

    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    public static class OptionItem {
        private String key;
        private String text;
    }
}
