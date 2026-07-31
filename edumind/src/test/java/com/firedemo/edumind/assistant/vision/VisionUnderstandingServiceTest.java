package com.firedemo.edumind.assistant.vision;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class VisionUnderstandingServiceTest {

    @Test
    void resolvesAssetIdAndDelegatesToModelClient() {
        VisualAssetService assets = mock(VisualAssetService.class);
        VisionModelClient modelClient = mock(VisionModelClient.class);
        CqImageMessageParser parser = new CqImageMessageParser();
        VisionUnderstandingService service =
                new VisionUnderstandingService(assets, modelClient, parser);

        String assetId = "b".repeat(64);
        VisualAsset asset = new VisualAsset(assetId, "image/png", 8, new byte[8]);
        VisualObservation expected = new VisualObservation(
                assetId, VisionTask.OCR, "text", "text",
                List.of(), 1.0, List.of(), "vision-model", 10);
        when(assets.get(assetId)).thenReturn(asset);
        when(modelClient.analyze(asset, VisionTask.OCR, "读取文字")).thenReturn(expected);

        VisualObservation actual = service.analyzeObservation(
                new VisionAnalysisRequest(assetId, "ocr", null, null, "读取文字"));

        assertEquals(expected, actual);
        verify(assets).get(assetId);
        verify(modelClient).analyze(asset, VisionTask.OCR, "读取文字");
    }
}
