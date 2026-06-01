package com.firedemo.demo.DTO;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 学生通过邀请码加入班级请求
 */
@Data
public class JoinByInviteDTO {

    @NotBlank(message = "邀请码不能为空")
    private String inviteCode;

    @NotBlank(message = "学号不能为空")
    private String studentId;

    @NotBlank(message = "姓名不能为空")
    private String studentName;
}
