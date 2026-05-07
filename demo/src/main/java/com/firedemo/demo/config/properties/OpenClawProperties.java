package com.firedemo.demo.config.properties;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * OpenClaw 配置属性
 */
@Data
@Component
@ConfigurationProperties(prefix = "openclaw.gateway")
public class OpenClawProperties {
    
    private String url;
    private String token;
    private String agent = "jarvis";
    private int connectTimeout = 5000;
    private int readTimeout = 120000;
    
    /** API 端点路径 */
    private String endpoint = "/v1/responses";
    
    /** 是否使用流式响应 */
    private boolean streaming = false;
    
    /** status 与 agent 映射：status=1用main，status=2用jarvis */
    private Map<Integer, String> statusAgentMapping = Map.of(
        1, "jarvis",
        2, "main"
    );
}
