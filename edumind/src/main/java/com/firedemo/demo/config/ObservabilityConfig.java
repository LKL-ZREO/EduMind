package com.firedemo.demo.config;

import io.micrometer.core.aop.TimedAspect;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 可观测性配置 — 启用 {@code @Timed} AOP 切面与自定义指标注册。
 */
@Configuration
public class ObservabilityConfig {

    /**
     * 启用 @Timed 注解的 AOP 切面。
     * 配合 {@code io.micrometer.core.annotation.Timed} 使用，
     * 自动记录方法耗时（_seconds_count / _seconds_sum / _seconds_max）。
     */
    @Bean
    public TimedAspect timedAspect(MeterRegistry registry) {
        return new TimedAspect(registry);
    }
}
