package com.firedemo.demo.eval.model;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

@Data
public class JudgeResult {
    @NotNull @Min(0) @Max(1)
    private Integer score;
    @Size(max = 50)
    private List<String> claims;
    @Size(max = 4_000)
    private String reason;
}
