package com.firedemo.demo.DTO;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

@Data
public class KnowledgeReclassificationResult {
    @NotNull @Valid @Size(max = 500)
    private List<Item> results;

    @Data
    public static class Item {
        @NotNull @Min(0)
        private Integer index;
        @NotBlank @Size(max = 200)
        private String knowledgePoint;
    }
}
