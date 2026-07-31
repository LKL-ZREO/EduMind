package com.firedemo.edumind.platform.web;

import com.firedemo.edumind.platform.ratelimit.BucketConfig;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.firedemo.edumind.platform.ratelimit.DistributedRateLimiter;
import com.firedemo.edumind.platform.ratelimit.TokenBucketInterceptor;
import org.springframework.context.annotation.Configuration;
import org.springframework.lang.NonNull;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

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
                        "/api/auth/**",
                        "/api/chat/health",
                        "/api/homework/classes",
                        "/api/homework/tasks",
                        "/api/homework/submit-status",
                        "/api/homework/result/**",
                        "/api/homework/check-qq-binding",
                        "/api/homework/submit",
                        "/api/homework/bind-qq",
                        "/api/onebot/rag",
                        "/api/teacher/classes/join",
                        "/api/live/join",
                        "/api/live/session/*",
                        "/api/preview/*",
                        "/actuator/**",
                        "/error"
                )
                .order(0);
    }
}
