package com.firedemo.demo.vision;

public record VisualAsset(
        String assetId,
        String mimeType,
        long size,
        byte[] content
) {
    public VisualAsset {
        content = content != null ? content.clone() : new byte[0];
    }

    @Override
    public byte[] content() {
        return content.clone();
    }
}
