package com.firedemo.demo.vision;

public interface VisionModelClient {

    VisualObservation analyze(VisualAsset asset, VisionTask task, String question);
}
