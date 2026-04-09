package com.firedemo.demo.Bean;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "openclaw.gateway")
public class OpenClawProperties {
    private String url;
    private String token;
    private String agent ="jarvis";
    private int connectTimeout = 5000;
    private int readTimeout = 120000;

    // 新增：API 端点路径选择
    private String endpoint = "/v1/responses";  // 改为 OpenResponses API

    // 新增：是否使用流式响应
    private boolean streaming = false;
}