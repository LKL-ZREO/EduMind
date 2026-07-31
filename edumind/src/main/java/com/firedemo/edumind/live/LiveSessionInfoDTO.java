package com.firedemo.edumind.live;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class LiveSessionInfoDTO {
    private Long sessionId;
    private String sessionCode;
    private String title;
    private String className;
    private String teacherName;
    private String token;
    private String studentId;
    private String studentName;
    private Boolean requiresStudentName;
    private InteractionPushDTO currentInteraction;
}
