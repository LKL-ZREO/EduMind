package com.firedemo.demo.DTO;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import lombok.Data;

// DTO：只用来接收前端注册表单
@Data
public class UserRegisterDTO {
    private String username;
    private String password;
    private String phone;
    private String email;
    private String status;
}