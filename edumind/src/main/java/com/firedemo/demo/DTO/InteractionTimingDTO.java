package com.firedemo.demo.DTO;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InteractionTimingDTO {
    private Long interactionId;
    private Long deadlineEpochMs;
    private Integer addedSeconds;
}
