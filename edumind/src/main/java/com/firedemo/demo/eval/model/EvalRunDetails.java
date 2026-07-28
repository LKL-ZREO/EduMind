package com.firedemo.demo.eval.model;

import com.firedemo.demo.eval.persistence.EvalCaseResultEntity;
import com.firedemo.demo.eval.persistence.EvalRunEntity;

import java.util.List;

public record EvalRunDetails(EvalRunEntity run, List<EvalCaseResultEntity> cases) {
    public EvalRunDetails {
        cases = cases == null ? List.of() : List.copyOf(cases);
    }
}
