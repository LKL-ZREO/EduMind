package com.firedemo.demo.DTO;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.util.List;
@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class QAMessageDTO {
    private List<QuestionItem> topQuestions;
    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    public static class QuestionItem {
        private Long id; private String question; private Integer similarCount;
        private Boolean answered; private String answerText; private String createdAt;
    }
}
