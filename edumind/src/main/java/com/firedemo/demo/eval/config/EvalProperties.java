package com.firedemo.demo.eval.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 评估服务配置。
 */
@Data
@Component
@ConfigurationProperties(prefix = "edumind.eval")
public class EvalProperties {

    /** 检索 Top-K 默认值 */
    private int defaultTopK = 5;

    /** 是否默认启用 Reranker */
    private boolean defaultEnableReranker = true;
}
