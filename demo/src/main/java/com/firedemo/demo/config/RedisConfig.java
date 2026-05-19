package com.firedemo.demo.config;

import org.redisson.Redisson;
import org.redisson.api.RBloomFilter;
import org.redisson.api.RedissonClient;
import org.redisson.config.Config;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Redisson 配置 — 连接池 + 布隆过滤器
 */
@Configuration
public class RedisConfig {

    @Value("${spring.data.redis.host:localhost}")
    private String redisHost;

    @Value("${spring.data.redis.port:6379}")
    private int redisPort;

    @Value("${spring.data.redis.database:0}")
    private int redisDatabase;

    @Bean(destroyMethod = "shutdown")
    public RedissonClient redissonClient() {
        Config config = new Config();
        config.useSingleServer()
                .setAddress("redis://" + redisHost + ":" + redisPort)
                .setDatabase(redisDatabase)
                .setConnectionPoolSize(32)
                .setConnectionMinimumIdleSize(8)
                .setRetryAttempts(3)
                .setRetryInterval(1500)
                .setTimeout(3000)
                .setConnectTimeout(10000);
        return Redisson.create(config);
    }

    /**
     * 布隆过滤器 — 缓存穿透防护
     * 拦截不存在的 classId 请求，避免打到 DB
     */
    @Bean
    public RBloomFilter<String> classIdBloomFilter(RedissonClient redissonClient) {
        RBloomFilter<String> filter = redissonClient.getBloomFilter("bloom:class:ids");
        filter.tryInit(10000L, 0.03);
        return filter;
    }
}
