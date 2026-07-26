package com.firedemo.demo.mcp.tools;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.firedemo.demo.agent.context.AgentChannel;
import com.firedemo.demo.agent.context.AgentExecutionContext;
import com.firedemo.demo.vision.VisionAnalysisRequest;
import com.firedemo.demo.vision.VisualObservation;
import com.firedemo.demo.vision.VisionTask;
import com.firedemo.demo.vision.VisionUnderstandingService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class VisionAnalyzeToolTest {

    @Test
    void prefersAssetIdAndReturnsStructuredObservation() {
        VisionUnderstandingService service = mock(VisionUnderstandingService.class);
        VisionAnalyzeTool tool = new VisionAnalyzeTool(service, new ObjectMapper());
        String assetId = "c".repeat(64);
        VisualObservation observation = new VisualObservation(
                assetId, VisionTask.DESCRIBE, "image summary", "",
                List.of(), 1.0, List.of(), "vision-model", 20);
        when(service.analyzeObservation(org.mockito.ArgumentMatchers.any()))
                .thenReturn(observation);

        String result = tool.execute(Map.of(
                "assetId", assetId,
                "task", "describe",
                "question", "图片里是什么？"
        ), new AgentExecutionContext(
                "session", 1L, null, Set.of(), AgentChannel.WEB, "trace"));

        assertTrue(result.contains(assetId));
        assertTrue(result.contains("image summary"));

        ArgumentCaptor<VisionAnalysisRequest> request =
                ArgumentCaptor.forClass(VisionAnalysisRequest.class);
        verify(service).analyzeObservation(request.capture());
        assertTrue(assetId.equals(request.getValue().assetId()));
        assertTrue("describe".equals(request.getValue().task()));
    }
}
