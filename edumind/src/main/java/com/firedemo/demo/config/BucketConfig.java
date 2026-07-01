package com.firedemo.demo.config;

import jakarta.validation.constraints.Min;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

/**
 * 令牌桶限流配置 — 按接口路径差异化
 *
 * <pre>
 * rate-limit:
 *   rules:
 *     /api/homework/submit:
 *       capacity: 5
 *       refill-per-minute: 3
 *     default:
 *       capacity: 30
 *       refill-per-minute: 20
 * </pre>
 */
@Data
@Component
@ConfigurationProperties(prefix = "rate-limit")
public class BucketConfig {

    /** 按接口路径配置的限流规则，key 为接口路径 */
    private Map<String, Rule> rules = new HashMap<>();

    /** 匿名路径（无需登录的接口）的默认规则 */
    private Rule anonymousDefault = new Rule(30, 20);

    /** 已认证路径的默认规则 */
    private Rule authenticatedDefault = new Rule(60, 40);

    @Data
    public static class Rule {
        /** 桶容量（允许的最大突发请求数） */
        @Min(1)
        private int capacity;
        /** 每分钟补充的令牌数（稳态速率） */
        @Min(1)
        private long refillPerMinute;

        public Rule() {}

        public Rule(int capacity, long refillPerMinute) {
            this.capacity = capacity;
            this.refillPerMinute = refillPerMinute;
        }
    }
}
