package com.firedemo.demo.config;

import com.firedemo.demo.config.properties.OpenClawProperties;
import org.springframework.boot.restclient.RestTemplateBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestTemplate;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;

/**
 * OpenClaw 配置
 */
@Configuration
public class OpenClawConfig {

    @Bean
    public RestTemplate openClawRestTemplate(OpenClawProperties properties) {
        return new RestTemplateBuilder()
                .connectTimeout(Duration.ofMillis(properties.getConnectTimeout()))
                .readTimeout(Duration.ofMillis(properties.getReadTimeout()))
                .build();
    }

    @Bean
    public ObjectMapper objectMapper() {
        return new ObjectMapper();
    }
}
