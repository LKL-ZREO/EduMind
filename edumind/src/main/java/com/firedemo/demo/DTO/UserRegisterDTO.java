package com.firedemo.demo.DTO;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class UserRegisterDTO {

    @NotBlank(message = "用户名不能为空")
    @Size(min = 2, max = 50, message = "用户名长度需在2-50之间")
    private String username;

    @NotBlank(message = "密码不能为空")
    @Size(min = 6, max = 128, message = "密码长度需在6-128之间")
    private String password;

    @Size(max = 20)
    private String phone;

    @Size(max = 100)
    @jakarta.validation.constraints.Email(message = "邮箱格式不正确")
    private String email;

    private String status;
}