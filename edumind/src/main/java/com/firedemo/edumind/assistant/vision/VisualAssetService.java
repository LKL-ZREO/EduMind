package com.firedemo.edumind.assistant.vision;

public interface VisualAssetService {

    VisualAsset importUrl(String url);

    VisualAsset importBase64(String base64OrDataUrl, String mimeType);

    VisualAsset importBytes(byte[] content, String mimeType);

    VisualAsset get(String assetId);
}