package com.firedemo.edumind.knowledge;

import com.firedemo.edumind.teaching.QuestionDTO;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

/** 一个知识库文档关联的预习材料与统一题目。 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DocumentMaterialsDTO {
    private List<PreviewSummary> previews;
    private List<QuestionDTO> questions;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PreviewSummary {
        private Long id;
        private String title;
        private String topic;
        private String status;
        private LocalDateTime createdAt;
    }
}
