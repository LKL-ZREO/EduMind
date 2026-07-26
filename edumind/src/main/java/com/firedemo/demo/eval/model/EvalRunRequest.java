package com.firedemo.demo.eval.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class EvalRunRequest {

    private List<String> metrics = List.of("faithfulness", "answer_relevancy");
    private int topK = 5;
    private boolean enableReranker = true;
    private boolean generateAnswers = true;
}
