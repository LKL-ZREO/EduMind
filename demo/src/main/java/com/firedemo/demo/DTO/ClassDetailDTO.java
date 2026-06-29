package com.firedemo.demo.DTO;

import lombok.Data;
import java.time.LocalDateTime;

/**
 * 班级详情响应（含学生列表）
 */
@Data
public class ClassDetailDTO {

    private Long id;
    private String name;
    private String courseGroup;
    private Long courseId;
    private String qqGroupId;
    private String description;
    private String inviteCode;
    private String status;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
