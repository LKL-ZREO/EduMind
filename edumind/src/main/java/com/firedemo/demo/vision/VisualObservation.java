package com.firedemo.demo.vision;

import java.util.List;

public record VisualObservation(
        String assetId,
        VisionTask task,
        String summary,
        String extractedText,
        List<String> objects,
        double confidence,
        List<String> warnings,
        String model,
        long elapsedMs
) {
    public VisualObservation {
        objects = objects != null ? List.copyOf(objects) : List.of();
        warnings = warnings != null ? List.copyOf(warnings) : List.of();
    }
}
