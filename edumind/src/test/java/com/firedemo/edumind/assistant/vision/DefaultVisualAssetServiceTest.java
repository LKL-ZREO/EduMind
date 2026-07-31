package com.firedemo.edumind.assistant.vision;

import com.firedemo.edumind.platform.storage.FileStorage;
import com.firedemo.edumind.shared.exception.BusinessException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.net.URI;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DefaultVisualAssetServiceTest {

    private FileStorage storage;
    private DefaultVisualAssetService service;

    @BeforeEach
    void setUp() {
        storage = mock(FileStorage.class);
        VisionProperties properties = new VisionProperties();
        properties.setMaxImageBytes(1024);
        properties.setAllowedHosts(List.of("multimedia.nt.qq.com.cn", "localhost"));
        service = new DefaultVisualAssetService(storage, properties);
    }

    @Test
    void importsValidPngAndStoresByStableAssetId() {
        byte[] png = pngBytes();
        when(storage.storeBytes(eq(png), anyString(), eq("image/png")))
                .thenAnswer(invocation -> invocation.getArgument(1));

        VisualAsset asset = service.importBytes(png, "image/png");

        assertEquals("image/png", asset.mimeType());
        assertEquals(64, asset.assetId().length());
        verify(storage).storeBytes(
                eq(png),
                eq("vision/assets/" + asset.assetId()),
                eq("image/png"));
    }

    @Test
    void rejectsContentThatIsNotAnImage() {
        assertThrows(BusinessException.class,
                () -> service.importBytes("not-an-image".getBytes(), "image/png"));
    }

    @Test
    void rejectsPrivateNetworkEvenWhenHostIsAllowed() {
        assertThrows(BusinessException.class,
                () -> service.importUrl("http://localhost/image.png"));
    }

    @Test
    void acceptsPrivateResolutionOnlyForExactTrustedHost() {
        VisionProperties properties = new VisionProperties();
        properties.setAllowedHosts(List.of("localhost"));
        properties.setTrustedPrivateResolutionHosts(List.of("localhost"));
        DefaultVisualAssetService trustedService = new DefaultVisualAssetService(storage, properties);

        assertDoesNotThrow(() -> trustedService.validateRemoteUri(URI.create("http://localhost/image.png")));
    }

    @Test
    void rejectsHostsOutsideAllowList() {
        assertThrows(BusinessException.class,
                () -> service.importUrl("https://example.com/image.png"));
    }

    @Test
    void loadsStoredAssetById() {
        byte[] png = pngBytes();
        String assetId = "a".repeat(64);
        when(storage.readFileBytes("vision/assets/" + assetId)).thenReturn(png);

        VisualAsset asset = service.get(assetId);

        assertEquals(assetId, asset.assetId());
        assertEquals("image/png", asset.mimeType());
    }

    private byte[] pngBytes() {
        return new byte[] {
                (byte) 0x89, 0x50, 0x4E, 0x47,
                0x0D, 0x0A, 0x1A, 0x0A
        };
    }
}
