package com.firedemo.demo.DTO;

import lombok.Data;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 课程分组响应（一门课程 + 其下的班级列表）
 */
@Data
public class ClassGroupDTO {

    private String courseGroup;
    private List<ClassItem> classes;

    @Data
    public static class ClassItem {
        private Long id;
        private String name;
        private String description;
        private Integer studentCount;
        private String inviteCode;
        private String status;
        private LocalDateTime createdAt;
    }
}
