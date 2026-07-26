package com.firedemo.demo.infrastructure.pdf;

import com.firedemo.demo.config.properties.VisionPdfProperties;
import com.firedemo.demo.vision.VisualAsset;
import com.firedemo.demo.vision.VisualAssetService;
import com.firedemo.demo.vision.VisualObservation;
import com.firedemo.demo.vision.VisionModelClient;
import com.firedemo.demo.vision.VisionTask;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class VisionPdfParserTest {

    private final VisualAssetService assetService = mock(VisualAssetService.class);
    private final VisionModelClient modelClient = mock(VisionModelClient.class);
    private final VisionPdfParser parser = new VisionPdfParser(assetService, modelClient, properties());

    @AfterEach
    void closeExecutor() {
        parser.shutdown();
    }

    @Test
    void routesRenderedPagesThroughVisualAssetAndModelBoundaries() {
        byte[] png = {1, 2, 3};
        VisualAsset asset = new VisualAsset("asset-1", "image/png", png.length, png);
        VisualObservation observation = new VisualObservation(
                asset.assetId(), VisionTask.OCR, "# Parsed page", "Parsed page",
                List.of(), 1.0, List.of(), "vision-model", 10);
        when(assetService.importBytes(png, "image/png")).thenReturn(asset);
        when(modelClient.analyze(eq(asset), eq(VisionTask.OCR), contains("page 1 of 1")))
                .thenReturn(observation);

        List<String> result = parser.processSequentialWithContext(
                List.of(new VisionPdfParser.PageImage(1, png)));

        assertThat(result).containsExactly("# Parsed page");
        verify(assetService).importBytes(png, "image/png");
        verify(modelClient).analyze(eq(asset), eq(VisionTask.OCR), contains("page 1 of 1"));
    }

    private static VisionPdfProperties properties() {
        VisionPdfProperties properties = new VisionPdfProperties();
        properties.setConcurrency(1);
        properties.setMaintainFormat(true);
        return properties;
    }
}
