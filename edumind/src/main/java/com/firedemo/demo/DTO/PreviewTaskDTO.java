package com.firedemo.demo.DTO;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class PreviewTaskDTO {
    private Long id;
    private Long classId;
    private String title;
    private String knowledgePoint;
    private String guideText;              // Markdown 导读材料
    private List<QuestionItem> questions;  // 自测题
    private String discussionQuestion;     // 讨论题
    private String status;
    private String createdAt;

    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    public static class QuestionItem {
        private String question;
        private List<OptionItem> options;  // null for open-ended
        private String correctKey;
        private String explanation;        // 解析
    }

    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    public static class OptionItem {
        private String key;
        private String text;
    }
}
