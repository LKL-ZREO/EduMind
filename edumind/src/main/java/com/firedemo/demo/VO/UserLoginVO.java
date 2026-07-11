package com.firedemo.demo.VO;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class UserLoginVO {
    private Long id;
    private String username;
    private String email;
    private String token;          // Access Token（JWT）
    private String refreshToken;   // Refresh Token（用于无感刷新）
    private Long expiresIn;        // Access Token 过期秒数
    private String sessionId;      // 当前会话ID
}
