package com.firedemo.edumind.live;

import com.baomidou.mybatisplus.annotation.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("live_attendance_log")
public class LiveAttendanceLog {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long sessionId;
    private Long classId;
    private String studentId;
    private String studentName;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime eventTime;

    private String event; // JOIN / LEAVE

    public static LiveAttendanceLog join(Long sessionId, Long classId, String studentId, String studentName) {
        return LiveAttendanceLog.builder()
                .sessionId(sessionId).classId(classId)
                .studentId(studentId).studentName(studentName)
                .event("JOIN").build();
    }

    public static LiveAttendanceLog leave(Long sessionId, Long classId, String studentId, String studentName) {
        return LiveAttendanceLog.builder()
                .sessionId(sessionId).classId(classId)
                .studentId(studentId).studentName(studentName)
                .event("LEAVE").build();
    }
}
