package com.firedemo.demo.config;

import com.firedemo.demo.Service.OpenClawService;
import com.firedemo.demo.Service.ServiceImpl.S3FileStorageServiceImpl;
import com.firedemo.demo.rag.EmbeddingService;
import com.firedemo.demo.rag.RerankerService;
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
    OpenClawService openClawService() {
        return mock(OpenClawService.class);
    }

    @Bean
    @Primary
    S3FileStorageServiceImpl s3FileStorageService() {
        return mock(S3FileStorageServiceImpl.class);
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
