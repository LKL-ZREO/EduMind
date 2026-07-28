package com.firedemo.demo.config.properties;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

@Data
@Component
@ConfigurationProperties(prefix = "edumind.vision")
public class VisionProperties {

    private long maxImageBytes = 20L * 1024 * 1024;

    private Duration connectTimeout = Duration.ofSeconds(10);

    private Duration readTimeout = Duration.ofSeconds(20);

    private int maxRedirects = 3;

    private List<String> allowedHosts = new ArrayList<>(List.of("multimedia.nt.qq.com.cn"));

    /**
     * Exact host names allowed to resolve to private addresses. This is intended for
     * trusted CDN hosts when a local proxy uses Fake-IP DNS.
     */
    private List<String> trustedPrivateResolutionHosts =
            new ArrayList<>(List.of("multimedia.nt.qq.com.cn"));
}
