package com.firedemo.demo.config.properties;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * OneBot Napcat 配置属性
 */
@Data
@Component
@ConfigurationProperties(prefix = "onebot")
public class OneBotProperties {
    
    private Http http = new Http();
    
    @Data
    public static class Http {
        private String url = "http://127.0.0.1:3000";
        private String token = "";
    }
}
