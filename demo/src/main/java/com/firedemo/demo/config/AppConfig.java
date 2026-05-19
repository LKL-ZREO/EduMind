package com.firedemo.demo.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 应用通用配置
 */
@Configuration
public class AppConfig {

    /**
     * Jackson ObjectMapper（供 Redis Stream 消费者等组件使用）
     */
    @Bean
    public ObjectMapper objectMapper() {
        return new ObjectMapper();
    }
}
