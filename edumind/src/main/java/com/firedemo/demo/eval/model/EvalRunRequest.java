package com.firedemo.demo.eval.model;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class EvalRunRequest {

    private static final String QUALITY_METRIC_PATTERN =
            "keyword_recall|content_coverage|hit_rate|mrr|ndcg|faithfulness|answer_relevancy";

    @NotEmpty
    private List<@Pattern(regexp = QUALITY_METRIC_PATTERN) String> metrics =
            List.of("keyword_recall", "content_coverage", "hit_rate", "mrr", "ndcg",
                    "faithfulness", "answer_relevancy");
    @Min(1) @Max(50)
    private int topK = 5;
    private boolean enableReranker = true;
    private boolean generateAnswers = true;
    @Size(max = 120)
    private String datasetVersion;
    private Map<
            @Pattern(regexp = QUALITY_METRIC_PATTERN) String,
            @NotNull @DecimalMin("0.0") @DecimalMax("1.0") Double> thresholds = Map.of();
}
