package com.firedemo.demo.vision;

public record VisionAnalysisRequest(
        String assetId,
        String task,
        String sourceType,
        String source,
        String prompt
) {
    public VisionAnalysisRequest(String sourceType, String source, String prompt) {
        this(null, null, sourceType, source, prompt);
    }
}
