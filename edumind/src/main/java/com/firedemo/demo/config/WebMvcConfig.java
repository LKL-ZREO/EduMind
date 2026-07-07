package com.firedemo.demo.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.firedemo.demo.common.limiter.DistributedRateLimiter;
import com.firedemo.demo.common.limiter.TokenBucketInterceptor;
import org.springframework.context.annotation.Configuration;
import org.springframework.lang.NonNull;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * WebMVC 配置 — 注册网关层令牌桶限流拦截器
 */
@Configuration
public class WebMvcConfig implements WebMvcConfigurer {

    private final DistributedRateLimiter rateLimiter;
    private final BucketConfig bucketConfig;
    private final ObjectMapper objectMapper;

    public WebMvcConfig(DistributedRateLimiter rateLimiter,
                        BucketConfig bucketConfig,
                        ObjectMapper objectMapper) {
        this.rateLimiter = rateLimiter;
        this.bucketConfig = bucketConfig;
        this.objectMapper = objectMapper;
    }

    @Override
    public void addInterceptors(@NonNull InterceptorRegistry registry) {
        registry.addInterceptor(new TokenBucketInterceptor(rateLimiter, bucketConfig, objectMapper))
                .addPathPatterns("/api/**")
                .excludePathPatterns(
                        "/api/auth/**",       // 认证接口不限流（登录、注册）
                        "/actuator/**",       // 健康检查不限流
                        "/error"             // 错误页面不限流
                )
                .order(0); // 最先执行，作为第一道防线
    }
}
