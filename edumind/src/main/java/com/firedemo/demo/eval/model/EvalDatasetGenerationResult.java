package com.firedemo.demo.eval.model;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

@Data
public class EvalDatasetGenerationResult {
    @NotEmpty @Valid @Size(max = 100)
    private List<Item> items;

    @Data
    public static class Item {
        @NotBlank @Size(max = 2_000)
        private String question;
        @NotBlank @Size(max = 8_000)
        private String answer;
    }
}
