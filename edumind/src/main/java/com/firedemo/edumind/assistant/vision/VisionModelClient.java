package com.firedemo.edumind.assistant.vision;

public interface VisionModelClient {

    VisualObservation analyze(VisualAsset asset, VisionTask task, String question);
}
