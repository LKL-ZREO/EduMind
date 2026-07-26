package com.firedemo.demo.config.properties;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * LLM 直连配置 — 替代 OpenClaw 网关
 *
 * <pre>
 *   edumind.llm.backend=built-in     # built-in | openclaw
 *   edumind.llm.base-url=...
 *   edumind.llm.api-key=...
 *   edumind.llm.model=kimi-k2-0701-preview
 *   edumind.llm.max-steps=10
 * </pre>
 */
@Data
@Component
@ConfigurationProperties(prefix = "edumind.llm")
public class LlmProperties {

    /** 后端模式：built-in（直连）或 openclaw（旧网关） */
    private String backend = "built-in";

    /** LLM API 地址（OpenAI 兼容） */
    private String baseUrl = "https://api.moonshot.cn/v1";

    /** LLM API Key */
    private String apiKey;

    /** 默认模型名 */
    private String model = "kimi-k2-0701-preview";

    private String textModel;

    private String visionModel;

    /** Vision model API address, defaults to the text model baseUrl when empty. */
    private String visionBaseUrl;

    /** Vision model API key, defaults to the text model apiKey when empty. */
    private String visionApiKey;

    /** 温度参数 */
    private double temperature = 0.2;

    private Double visionTemperature = 1.0;

    /** 连接超时 */
    private Duration connectTimeout = Duration.ofSeconds(10);

    /** 读取超时 */
    private Duration readTimeout = Duration.ofMinutes(5);

    /** Agent 循环最大步数 */
    private int maxSteps = 10;

    /** 单个工具调用超时 */
    private Duration toolTimeout = Duration.ofSeconds(30);

    /** 是否启用自我反思（Agent 在最终回答前让 LLM 检查自己的输出质量） */
    private boolean selfReflection = true;

    public String resolveTextModel() {
        return hasText(textModel) ? textModel : model;
    }

    public String resolveVisionModel() {
        return hasText(visionModel) ? visionModel : resolveTextModel();
    }

    public String resolveVisionBaseUrl() {
        return hasText(visionBaseUrl) ? visionBaseUrl : baseUrl;
    }

    public String resolveVisionApiKey() {
        return hasText(visionApiKey) ? visionApiKey : apiKey;
    }

    public double resolveVisionTemperature() {
        return visionTemperature != null ? visionTemperature : temperature;
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
