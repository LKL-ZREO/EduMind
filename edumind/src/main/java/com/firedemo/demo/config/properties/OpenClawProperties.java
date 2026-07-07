package com.firedemo.demo.config.properties;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * OpenClaw Agent 路由映射配置
 */
@Data
@Component
@ConfigurationProperties(prefix = "openclaw.agent")
public class OpenClawProperties {
    
    /** 默认 agent ID */
    private String defaultAgent = "jarvis";
    
    /** status → agentId 映射 */
    private Map<Integer, String> mapping = Map.of(
        1, "jarvis",
        2, "main"
    );
}
