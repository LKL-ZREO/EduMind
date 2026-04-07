package com.firedemo.demo.DTO;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class StreamChunk {
    private String content;
    private boolean done;
}