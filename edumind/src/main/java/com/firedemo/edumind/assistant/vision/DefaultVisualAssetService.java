package com.firedemo.edumind.assistant.vision;

import com.firedemo.edumind.platform.storage.FileStorage;
import com.firedemo.edumind.shared.exception.BusinessException;
import com.firedemo.edumind.shared.exception.ErrorCode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.net.InetAddress;
import java.net.URI;
import java.net.UnknownHostException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Locale;

@Slf4j
@Service
public class DefaultVisualAssetService implements VisualAssetService {

    private static final String STORAGE_PREFIX = "vision/assets/";

    private final FileStorage fileStorageService;
    private final VisionProperties properties;
    private final HttpClient httpClient;

    public DefaultVisualAssetService(FileStorage fileStorageService,
                                     VisionProperties properties) {
        this.fileStorageService = fileStorageService;
        this.properties = properties;
        this.httpClient = HttpClient.newBuilder()
                .followRedirects(HttpClient.Redirect.NEVER)
                .connectTimeout(properties.getConnectTimeout())
                .build();
    }

    @Override
    public VisualAsset importUrl(String url) {
        if (url == null || url.isBlank()) {
            throw parameterError("Image URL is required");
        }

        URI current = parseUri(normalizeUrl(url));
        for (int redirect = 0; redirect <= properties.getMaxRedirects(); redirect++) {
            validateRemoteUri(current);
            HttpResponse<InputStream> response = send(current);
            int status = response.statusCode();

            if (status >= 300 && status < 400) {
                closeQuietly(response.body());
                String location = response.headers().firstValue("Location")
                        .orElseThrow(() -> parameterError("Image redirect is missing Location"));
                current = current.resolve(location);
                continue;
            }

            if (status < 200 || status >= 300) {
                closeQuietly(response.body());
                throw new BusinessException(ErrorCode.AI_SERVICE_ERROR.getCode(),
                        "Image download failed with HTTP status " + status);
            }

            long contentLength = response.headers().firstValueAsLong("Content-Length").orElse(-1L);
            if (contentLength > properties.getMaxImageBytes()) {
                closeQuietly(response.body());
                throw parameterError("Image exceeds configured size limit");
            }

            byte[] content = readBounded(response.body());
            String declaredType = response.headers().firstValue("Content-Type")
                    .map(value -> value.split(";")[0].trim())
                    .orElse(null);
            return importBytes(content, declaredType);
        }

        throw parameterError("Too many image redirects");
    }

    @Override
    public VisualAsset importBase64(String base64OrDataUrl, String mimeType) {
        if (base64OrDataUrl == null || base64OrDataUrl.isBlank()) {
            throw parameterError("Base64 image content is required");
        }

        String encoded = base64OrDataUrl.trim();
        String declaredType = mimeType;
        if (encoded.startsWith("data:")) {
            int comma = encoded.indexOf(',');
            if (comma < 0) throw parameterError("Invalid image data URL");
            String metadata = encoded.substring(5, comma);
            String[] parts = metadata.split(";");
            declaredType = parts.length > 0 ? parts[0] : mimeType;
            encoded = encoded.substring(comma + 1);
        }

        try {
            return importBytes(Base64.getDecoder().decode(encoded), declaredType);
        } catch (IllegalArgumentException e) {
            throw parameterError("Invalid base64 image content");
        }
    }

    @Override
    public VisualAsset importBytes(byte[] content, String mimeType) {
        if (content == null || content.length == 0) {
            throw parameterError("Image content is empty");
        }
        if (content.length > properties.getMaxImageBytes()) {
            throw parameterError("Image exceeds configured size limit");
        }

        String detectedType = detectMimeType(content);
        if (detectedType == null) {
            throw parameterError("Unsupported or invalid image content");
        }
        if (mimeType != null && !mimeType.isBlank() && !mimeType.startsWith("image/")) {
            throw parameterError("Content-Type is not an image");
        }

        String assetId = sha256(content);
        fileStorageService.storeBytes(content, storageKey(assetId), detectedType);
        log.info("Visual asset imported: assetId={}, mimeType={}, size={}",
                assetId, detectedType, content.length);
        return new VisualAsset(assetId, detectedType, content.length, content);
    }

    @Override
    public VisualAsset get(String assetId) {
        validateAssetId(assetId);
        byte[] content = fileStorageService.readFileBytes(storageKey(assetId));
        String mimeType = detectMimeType(content);
        if (mimeType == null) {
            throw parameterError("Stored visual asset is not a supported image");
        }
        return new VisualAsset(assetId, mimeType, content.length, content);
    }

    private HttpResponse<InputStream> send(URI uri) {
        HttpRequest request = HttpRequest.newBuilder(uri)
                .GET()
                .timeout(properties.getReadTimeout())
                .header("User-Agent", "EduMind-Vision/1.0")
                .build();
        try {
            return httpClient.send(request, HttpResponse.BodyHandlers.ofInputStream());
        } catch (IOException e) {
            throw new BusinessException(ErrorCode.AI_SERVICE_ERROR.getCode(),
                    "Image download failed: " + e.getMessage());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new BusinessException(ErrorCode.AI_SERVICE_ERROR.getCode(),
                    "Image download was interrupted");
        }
    }

    private byte[] readBounded(InputStream inputStream) {
        int maxBytes = (int) Math.min(properties.getMaxImageBytes(), Integer.MAX_VALUE - 1L);
        try (InputStream in = inputStream) {
            byte[] content = in.readNBytes(maxBytes + 1);
            if (content.length > maxBytes) {
                throw parameterError("Image exceeds configured size limit");
            }
            return content;
        } catch (IOException e) {
            throw new BusinessException(ErrorCode.AI_SERVICE_ERROR.getCode(),
                    "Failed to read downloaded image: " + e.getMessage());
        }
    }

    void validateRemoteUri(URI uri) {
        String scheme = uri.getScheme();
        String host = uri.getHost();
        if (!("http".equalsIgnoreCase(scheme) || "https".equalsIgnoreCase(scheme))
                || host == null || uri.getUserInfo() != null) {
            throw parameterError("Invalid image URL");
        }
        if (!isAllowedHost(host)) {
            throw parameterError("Image host is not allowed: " + host);
        }

        try {
            for (InetAddress address : InetAddress.getAllByName(host)) {
                if (isPrivateOrLocal(address)
                        && !isTrustedPrivateResolutionHost(host)) {
                    throw parameterError("Private or local image addresses are not allowed");
                }
            }
        } catch (UnknownHostException e) {
            throw parameterError("Image host cannot be resolved");
        }
    }

    private boolean isPrivateOrLocal(InetAddress address) {
        return address.isAnyLocalAddress()
                        || address.isLoopbackAddress()
                        || address.isLinkLocalAddress()
                        || address.isSiteLocalAddress()
                        || address.isMulticastAddress();
    }

    private boolean isTrustedPrivateResolutionHost(String host) {
        String normalizedHost = host.toLowerCase(Locale.ROOT);
        return properties.getTrustedPrivateResolutionHosts().stream()
                .filter(value -> value != null && !value.isBlank())
                .map(value -> value.trim().toLowerCase(Locale.ROOT))
                .anyMatch(normalizedHost::equals);
    }

    private boolean isAllowedHost(String host) {
        String normalizedHost = host.toLowerCase(Locale.ROOT);
        return properties.getAllowedHosts().stream()
                .filter(value -> value != null && !value.isBlank())
                .map(value -> value.trim().toLowerCase(Locale.ROOT))
                .anyMatch(allowed -> normalizedHost.equals(allowed)
                        || normalizedHost.endsWith("." + allowed));
    }

    private URI parseUri(String url) {
        try {
            return URI.create(url);
        } catch (IllegalArgumentException e) {
            throw parameterError("Invalid image URL");
        }
    }

    private String normalizeUrl(String value) {
        return value.trim()
                .replace("&amp;", "&")
                .replace("\\u0026", "&")
                .replace("\\u003d", "=")
                .replace("\\u003f", "?");
    }

    private String detectMimeType(byte[] bytes) {
        if (bytes.length >= 8
                && bytes[0] == (byte) 0x89 && bytes[1] == 0x50
                && bytes[2] == 0x4E && bytes[3] == 0x47) return "image/png";
        if (bytes.length >= 3
                && bytes[0] == (byte) 0xFF && bytes[1] == (byte) 0xD8
                && bytes[2] == (byte) 0xFF) return "image/jpeg";
        if (bytes.length >= 6
                && bytes[0] == 0x47 && bytes[1] == 0x49
                && bytes[2] == 0x46) return "image/gif";
        if (bytes.length >= 2 && bytes[0] == 0x42 && bytes[1] == 0x4D) return "image/bmp";
        if (bytes.length >= 12
                && bytes[0] == 0x52 && bytes[1] == 0x49 && bytes[2] == 0x46 && bytes[3] == 0x46
                && bytes[8] == 0x57 && bytes[9] == 0x45 && bytes[10] == 0x42 && bytes[11] == 0x50) {
            return "image/webp";
        }
        return null;
    }

    private String sha256(byte[] content) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(content));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is unavailable", e);
        }
    }

    private void validateAssetId(String assetId) {
        if (assetId == null || !assetId.matches("[a-f0-9]{64}")) {
            throw parameterError("Invalid visual asset ID");
        }
    }

    private String storageKey(String assetId) {
        return STORAGE_PREFIX + assetId;
    }

    private void closeQuietly(InputStream inputStream) {
        if (inputStream == null) return;
        try {
            inputStream.close();
        } catch (IOException ignored) {
        }
    }

    private BusinessException parameterError(String message) {
        return new BusinessException(ErrorCode.PARAM_ERROR.getCode(), message);
    }
}
