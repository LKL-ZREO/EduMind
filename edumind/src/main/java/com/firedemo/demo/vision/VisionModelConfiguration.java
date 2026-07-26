package com.firedemo.demo.vision;

import com.firedemo.demo.common.exception.BusinessException;
import com.firedemo.demo.common.exception.ErrorCode;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class VisionModelConfiguration {

    @Bean
    @ConditionalOnMissingBean(VisionModelClient.class)
    VisionModelClient unavailableVisionModelClient() {
        return (asset, task, question) -> {
            throw new BusinessException(
                    ErrorCode.AI_SERVICE_ERROR.getCode(),
                    "Vision model is unavailable for the configured LLM backend");
        };
    }
}
