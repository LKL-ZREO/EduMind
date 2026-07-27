package com.firedemo.demo.config.observability;

import com.firedemo.demo.config.properties.HeliconeProperties;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class HeliconeHeadersFactoryTest {

    @Test
    void keepsProviderRoutingUnchangedWhenDisabled() {
        HeliconeProperties properties = new HeliconeProperties();
        HeliconeHeadersFactory factory = new HeliconeHeadersFactory(properties);

        assertThat(factory.resolveBaseUrl(" https://provider.example/v1 "))
                .isEqualTo("https://provider.example/v1");
        assertThat(factory.create("https://provider.example/v1", "text")).isEmpty();
    }

    @Test
    void buildsPassiveObservabilityHeadersWhenEnabled() {
        HeliconeProperties properties = enabledProperties();
        HeliconeHeadersFactory factory = new HeliconeHeadersFactory(properties);

        Map<String, String> headers = factory.create("https://provider.example/v1", "vision");

        assertThat(factory.resolveBaseUrl("https://provider.example/v1"))
                .isEqualTo("https://gateway.helicone.test/v1");
        assertThat(headers)
                .containsEntry("Helicone-Auth", "Bearer helicone-secret")
                .containsEntry("Helicone-Target-URL", "https://provider.example/v1")
                .containsEntry("Helicone-Property-Application", "edumind")
                .containsEntry("Helicone-Property-ModelRole", "vision")
                .containsEntry("Helicone-Property-Environment", "test")
                .containsEntry("Helicone-Omit-Request", "true")
                .containsEntry("Helicone-Omit-Response", "true")
                .doesNotContainKey("Authorization");
    }

    @Test
    void allowsContentLoggingOnlyWhenExplicitlyConfigured() {
        HeliconeProperties properties = enabledProperties();
        properties.setOmitContent(false);
        HeliconeHeadersFactory factory = new HeliconeHeadersFactory(properties);

        assertThat(factory.create("https://provider.example/v1", "text"))
                .doesNotContainKeys("Helicone-Omit-Request", "Helicone-Omit-Response");
    }

    @Test
    void failsFastWhenEnabledWithoutApiKey() {
        HeliconeProperties properties = enabledProperties();
        properties.setApiKey(" ");
        HeliconeHeadersFactory factory = new HeliconeHeadersFactory(properties);

        assertThatThrownBy(() -> factory.create("https://provider.example/v1", "text"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Helicone API key");
    }

    @Test
    void doesNotExposeApiKeyInPropertiesToString() {
        HeliconeProperties properties = enabledProperties();

        assertThat(properties.toString()).doesNotContain("helicone-secret");
    }

    private HeliconeProperties enabledProperties() {
        HeliconeProperties properties = new HeliconeProperties();
        properties.setEnabled(true);
        properties.setGatewayUrl("https://gateway.helicone.test/v1");
        properties.setApiKey("helicone-secret");
        properties.setEnvironment("test");
        return properties;
    }
}
