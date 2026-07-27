package com.firedemo.demo.config.properties;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.time.Duration;

/** Configuration for token-bounded, persistent Agent working memory. */
@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "edumind.agent.memory")
public class AgentMemoryProperties {

    /** Input-memory budget, leaving room for tools, the current turn, and model output. */
    private int maxTokens = 12_000;

    /** Compatible tokenizer used as a conservative estimator for OpenAI-style providers. */
    private String tokenEstimatorModel = "gpt-4o-mini";

    /** Maximum number of hot conversations retained in one application instance. */
    private long maxActiveSessions = 10_000;

    /** Idle hot conversations are evicted locally and restored from PostgreSQL on demand. */
    private Duration expireAfterAccess = Duration.ofHours(2);
}
