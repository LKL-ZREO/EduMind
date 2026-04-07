package com.firedemo.demo.DTO;

import lombok.Data;

@Data
public class ChatRequest {
    private String message;
    private String sessionId;  // 可选，用于保持会话
}