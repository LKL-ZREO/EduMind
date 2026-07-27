package com.firedemo.demo.config.properties;

import lombok.Getter;
import lombok.Setter;
import lombok.ToString;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Optional Helicone gateway configuration for passive LLM observability.
 */
@Getter
@Setter
@ToString(exclude = "apiKey")
@Component
@ConfigurationProperties(prefix = "edumind.observability.helicone")
public class HeliconeProperties {

    /** Disabled by default so local development and CI keep using provider endpoints directly. */
    private boolean enabled;

    /** OpenAI-compatible Helicone pass-through gateway endpoint. */
    private String gatewayUrl = "https://gateway.helicone.ai/v1";

    /** Helicone write API key. This is separate from the model provider API key. */
    private String apiKey;

    /** Keep token, cost, and latency metadata without storing prompts or model responses. */
    private boolean omitContent = true;

    /** Low-cardinality deployment label attached to Helicone requests. */
    private String environment = "dev";
}
