package com.firedemo.demo.VO;

import lombok.Data;

import lombok.Builder;

@Data
@Builder
public class UserLoginVO {
    private Long id;
    private String username;
    private String email;
    private String token;  // JWT Token
}