package com.firedemo.demo.infrastructure.ai;

import java.util.List;

/** A bounded, model-readable description of semantic output violations. */
public class StructuredOutputValidationException extends RuntimeException {

    private final List<String> violations;

    public StructuredOutputValidationException(List<String> violations) {
        super("SEMANTIC_VALIDATION_FAILED: " + String.join("; ", violations));
        this.violations = List.copyOf(violations);
    }

    public List<String> violations() {
        return violations;
    }
}
