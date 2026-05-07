package com.firedemo.demo.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.netty.http.client.HttpClient;

import java.time.Duration;

/**
 * 应用配置
 */
@Configuration
public class AppConfig {

    @Value("${openclaw.gateway.url:http://localhost:18789}")
    private String openClawUrl;

    @Value("${openclaw.gateway.token}")
    private String openClawToken;

    @Bean
    public WebClient openClawWebClient() {
        HttpClient httpClient = HttpClient.create()
                .responseTimeout(Duration.ofSeconds(120));

        return WebClient.builder()
                .baseUrl(openClawUrl)
                .defaultHeader("Authorization", "Bearer " + openClawToken)
                .defaultHeader("Content-Type", "application/json")
                .defaultHeader("x-openclaw-agent-id", "main")
                .clientConnector(new ReactorClientHttpConnector(httpClient))
                .build();
    }
}
