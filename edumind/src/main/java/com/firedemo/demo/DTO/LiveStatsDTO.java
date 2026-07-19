package com.firedemo.demo.DTO;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.util.List;
import java.util.Map;
@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class LiveStatsDTO {
    private Long interactionId;
    private String status;
    private int totalStudents;
    private int respondedCount;
    private Map<String, DistributionItem> distribution;
    private Double correctRate;
    private List<String> unrespondedStudents;
    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    public static class DistributionItem { private int count; private double percent; }
}
