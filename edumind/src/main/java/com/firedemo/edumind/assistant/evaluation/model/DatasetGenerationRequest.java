package com.firedemo.edumind.assistant.evaluation.model;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 数据集生成请求。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class DatasetGenerationRequest {

    private List<String> docIds;
    @Min(1) @Max(20)
    private int questionCount = 5;
}
