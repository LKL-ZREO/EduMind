package com.firedemo.demo.config.properties;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LlmPropertiesTest {

    @Test
    void visionTemperatureIsIndependentFromTextTemperature() {
        LlmProperties properties = new LlmProperties();
        properties.setTemperature(0.2);
        properties.setVisionTemperature(1.0);

        assertEquals(0.2, properties.getTemperature());
        assertEquals(1.0, properties.resolveVisionTemperature());
    }

    @Test
    void acceptsConfiguredProviderCredentials() {
        LlmProperties properties = new LlmProperties();
        properties.setApiKey("provider-key");

        assertDoesNotThrow(properties::validateForBuiltIn);
    }

    @Test
    void rejectsPlaceholderTextApiKeyWithoutLeakingIt() {
        LlmProperties properties = new LlmProperties();
        properties.setApiKey("change-me");

        IllegalStateException error = assertThrows(
                IllegalStateException.class,
                properties::validateForBuiltIn);

        assertTrue(error.getMessage().contains("LLM_API_KEY"));
        assertTrue(error.getMessage().contains("placeholder"));
        assertFalse(error.getMessage().contains("change-me"));
    }

    @Test
    void rejectsPlaceholderVisionApiKey() {
        LlmProperties properties = new LlmProperties();
        properties.setApiKey("provider-key");
        properties.setVisionApiKey("replace-me");

        IllegalStateException error = assertThrows(
                IllegalStateException.class,
                properties::validateForBuiltIn);

        assertTrue(error.getMessage().contains("LLM_VISION_API_KEY"));
    }
}
