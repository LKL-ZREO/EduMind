package com.firedemo.demo.vision;

import com.firedemo.demo.common.exception.BusinessException;
import com.firedemo.demo.common.exception.ErrorCode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Locale;

@Slf4j
@Service
public class VisionUnderstandingService {

    private final VisualAssetService visualAssetService;
    private final VisionModelClient visionModelClient;
    private final CqImageMessageParser cqImageMessageParser;

    public VisionUnderstandingService(VisualAssetService visualAssetService,
                                      VisionModelClient visionModelClient,
                                      CqImageMessageParser cqImageMessageParser) {
        this.visualAssetService = visualAssetService;
        this.visionModelClient = visionModelClient;
        this.cqImageMessageParser = cqImageMessageParser;
    }

    /** Compatibility entry point used by the current MCP tool. */
    public String analyze(VisionAnalysisRequest request) {
        return analyzeObservation(request).summary();
    }

    public VisualObservation analyzeObservation(VisionAnalysisRequest request) {
        if (request == null) {
            throw new BusinessException(ErrorCode.PARAM_ERROR);
        }

        VisionTask task = VisionTask.from(request.task());
        VisualAsset asset = resolveAsset(request);
        log.info("Vision analysis started: assetId={}, task={}, promptLen={}",
                asset.assetId(), task, request.prompt() != null ? request.prompt().length() : 0);
        return visionModelClient.analyze(asset, task, request.prompt());
    }

    public VisualObservation analyze(String assetId, VisionTask task, String question) {
        VisualAsset asset = visualAssetService.get(assetId);
        return visionModelClient.analyze(asset, task != null ? task : VisionTask.DESCRIBE, question);
    }

    private VisualAsset resolveAsset(VisionAnalysisRequest request) {
        if (hasText(request.assetId())) {
            return visualAssetService.get(request.assetId());
        }
        if (!hasText(request.source())) {
            throw new BusinessException(ErrorCode.PARAM_ERROR.getCode(),
                    "assetId or legacy source is required");
        }

        String sourceType = normalizeSourceType(request.sourceType(), request.source());
        return switch (sourceType) {
            case "cq" -> visualAssetService.importUrl(
                    cqImageMessageParser.extractImageUrl(request.source())
                            .orElseThrow(() -> new BusinessException(
                                    ErrorCode.PARAM_ERROR.getCode(),
                                    "CQ image message does not contain a URL")));
            case "url" -> visualAssetService.importUrl(request.source());
            case "base64" -> visualAssetService.importBase64(request.source(), null);
            default -> throw new BusinessException(ErrorCode.PARAM_ERROR.getCode(),
                    "Unsupported visual source type");
        };
    }

    private String normalizeSourceType(String sourceType, String source) {
        if (hasText(sourceType)) {
            return sourceType.trim().toLowerCase(Locale.ROOT);
        }
        String trimmed = source.trim();
        if (trimmed.startsWith("[CQ:image")) return "cq";
        if (trimmed.startsWith("http://") || trimmed.startsWith("https://")) return "url";
        return "base64";
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
