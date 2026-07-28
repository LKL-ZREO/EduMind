package com.firedemo.demo.DTO;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class TimelineDTO {
    private List<WeekGroup> weeks;

    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    public static class WeekGroup {
        private int weekNumber;
        private String label;
        private List<WeekItem> items;
    }

    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    public static class WeekItem {
        private String type;            // plan / session / homework / preview
        private String typeLabel;
        private String icon;
        private Long id;
        private String title;
        private String date;
        private String time;
        private String status;          // PLANNED / COMPLETED / ACTIVE / closed
        private String detail;
        // 课堂结果
        private Integer interactionCount;
        private Double avgCorrectRate;
        private String topConfusion;
    }
}
