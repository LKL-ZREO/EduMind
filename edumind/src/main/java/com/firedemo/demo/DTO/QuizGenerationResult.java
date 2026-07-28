package com.firedemo.demo.DTO;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

@Data
public class QuizGenerationResult {
    @NotEmpty @Valid @Size(max = 20)
    private List<GenerateMaterialsResponse.QuizItem> quizzes;
}
