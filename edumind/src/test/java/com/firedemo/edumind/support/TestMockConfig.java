package com.firedemo.edumind.support;

import com.firedemo.edumind.assistant.AgentService;
import com.firedemo.edumind.integration.storage.S3FileStorage;
import com.firedemo.edumind.knowledge.retrieval.EmbeddingService;
import com.firedemo.edumind.knowledge.retrieval.RerankerService;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

import static org.mockito.Mockito.mock;

/**
 * 测试环境 Mock 配置 — 替代外部服务依赖。
 * <p>
 * 由 {@code @Import(TestMockConfig.class)} 按需引入。
 */
@TestConfiguration
public class TestMockConfig {

    @Bean
    @Primary
    AgentService agentService() {
        return mock(AgentService.class);
    }

    @Bean
    @Primary
    S3FileStorage s3FileStorage() {
        return mock(S3FileStorage.class);
    }

    @Bean
    @Primary
    EmbeddingService embeddingService() {
        return mock(EmbeddingService.class);
    }

    @Bean
    @Primary
    RerankerService rerankerService() {
        return mock(RerankerService.class);
    }
}
