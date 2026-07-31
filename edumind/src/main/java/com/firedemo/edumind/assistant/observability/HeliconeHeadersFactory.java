package com.firedemo.edumind.assistant.observability;

import com.firedemo.edumind.assistant.config.HeliconeProperties;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Resolves optional Helicone routing without exposing gateway details to model callers.
 */
@Component
public class HeliconeHeadersFactory {

    private static final String APPLICATION_NAME = "edumind";

    private final HeliconeProperties properties;

    public HeliconeHeadersFactory(HeliconeProperties properties) {
        this.properties = properties;
    }

    public boolean isEnabled() {
        return properties.isEnabled();
    }

    /**
     * Uses the provider URL directly unless passive Helicone routing is enabled.
     */
    public String resolveBaseUrl(String providerBaseUrl) {
        String targetUrl = requireText(providerBaseUrl, "LLM provider base URL");
        if (!properties.isEnabled()) {
            return targetUrl;
        }
        return requireText(properties.getGatewayUrl(), "Helicone gateway URL");
    }

    /**
     * Builds only Helicone metadata headers. The provider key remains the HTTP Authorization header.
     */
    public Map<String, String> create(String providerBaseUrl, String modelRole) {
        if (!properties.isEnabled()) {
            return Map.of();
        }

        Map<String, String> headers = new LinkedHashMap<>();
        headers.put("Helicone-Auth", "Bearer " + requireText(properties.getApiKey(), "Helicone API key"));
        headers.put("Helicone-Target-URL", requireText(providerBaseUrl, "LLM provider base URL"));
        headers.put("Helicone-Property-Application", APPLICATION_NAME);
        headers.put("Helicone-Property-ModelRole", requireText(modelRole, "Helicone model role"));
        headers.put("Helicone-Property-Environment",
                requireText(properties.getEnvironment(), "Helicone environment"));

        if (properties.isOmitContent()) {
            headers.put("Helicone-Omit-Request", "true");
            headers.put("Helicone-Omit-Response", "true");
        }
        return Map.copyOf(headers);
    }

    private String requireText(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalStateException(name + " is required when Helicone routing is enabled");
        }
        return value.trim();
    }
}
