package com.firedemo.demo.config.properties;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class LlmPropertiesTest {

    @Test
    void visionTemperatureIsIndependentFromTextTemperature() {
        LlmProperties properties = new LlmProperties();
        properties.setTemperature(0.2);
        properties.setVisionTemperature(1.0);

        assertEquals(0.2, properties.getTemperature());
        assertEquals(1.0, properties.resolveVisionTemperature());
    }
}
