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
    /** 最近的 AI 对话会话 ID，不是 HTTP Session ID。 */
    private String sessionId;
}
