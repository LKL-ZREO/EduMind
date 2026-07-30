package com.firedemo.demo.DTO;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 学生综合诊断：统一返回成绩轨迹、风险原因、薄弱知识点和近期错误。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StudentInsightDTO {

    private StudentIdentity student;
    private Summary summary;
    private Risk risk;
    private List<ScorePoint> scoreHistory;
    private List<WeakKnowledgePoint> weakKnowledgePoints;
    private List<RecentError> recentErrors;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class StudentIdentity {
        private Long id;
        private String studentId;
        private String name;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Summary {
        private Integer avgScore;
        private Integer latestScore;
        private Integer highestScore;
        private Integer lowestScore;
        private Integer completedCount;
        private Integer lateCount;
        private Integer totalErrorCount;
        private Integer criticalErrorCount;
        private Integer latestChange;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Risk {
        /** LOW / MEDIUM / HIGH */
        private String level;
        private List<String> reasons;
        private List<String> suggestions;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ScorePoint {
        private Integer no;
        private Long submissionId;
        private String assignmentName;
        private String date;
        private Integer score;
        private Integer change;
        private Boolean late;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class WeakKnowledgePoint {
        private String name;
        private Integer errorCount;
        private Integer criticalCount;
        private LocalDateTime latestSeenAt;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RecentError {
        private Long id;
        private Long submissionId;
        private String assignmentName;
        private String knowledgePoint;
        private String errorText;
        private String severity;
        private LocalDateTime createdAt;
    }
}
