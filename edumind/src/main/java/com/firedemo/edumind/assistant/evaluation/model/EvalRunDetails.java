package com.firedemo.edumind.assistant.evaluation.model;

import com.firedemo.edumind.assistant.evaluation.persistence.EvalCaseResultEntity;
import com.firedemo.edumind.assistant.evaluation.persistence.EvalRunEntity;

import java.util.List;

public record EvalRunDetails(EvalRunEntity run, List<EvalCaseResultEntity> cases) {
    public EvalRunDetails {
        cases = cases == null ? List.of() : List.copyOf(cases);
    }
}
