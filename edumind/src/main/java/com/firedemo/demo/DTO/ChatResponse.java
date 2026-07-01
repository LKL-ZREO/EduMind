package com.firedemo.demo.DTO;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class ChatResponse {
    private String content;
    private String role;
    private Long timestamp;
    private String model;
    private String sessionId;
    private boolean done;
}