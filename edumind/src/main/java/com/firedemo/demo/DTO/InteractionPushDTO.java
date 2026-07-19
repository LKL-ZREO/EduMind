package com.firedemo.demo.DTO;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.util.List;
@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class InteractionPushDTO {
    private Long interactionId;
    private String type;
    private String status;
    private String title;
    private String description;
    private List<OptionItem> options;
    private String correctKey;
    private Integer timeLimit;
    private Long deadlineEpochMs;  // 绝对截止时间戳(ms)，学生端用于精确倒计时
    private String serverTime;
    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    public static class OptionItem { private String key; private String text; }
}
