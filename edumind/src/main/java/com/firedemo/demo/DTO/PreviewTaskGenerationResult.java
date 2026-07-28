package com.firedemo.demo.DTO;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

/** Validated output contract for AI-generated preview tasks. */
@Data
public class PreviewTaskGenerationResult {
    @NotBlank @Size(max = 10_000)
    private String guide;
    @NotEmpty @Valid @Size(max = 20)
    private List<Question> questions;
    @NotBlank @Size(max = 2_000)
    private String discussion;

    @Data
    public static class Question {
        @NotBlank @Size(max = 2_000)
        private String question;
        @Valid @Size(max = 10)
        private List<Option> options;
        @NotBlank @Size(max = 2_000)
        private String correctKey;
        @NotBlank @Size(max = 4_000)
        private String explanation;
    }

    @Data
    public static class Option {
        @NotBlank @Size(max = 10)
        private String key;
        @NotBlank @Size(max = 1_000)
        private String text;
    }
}
