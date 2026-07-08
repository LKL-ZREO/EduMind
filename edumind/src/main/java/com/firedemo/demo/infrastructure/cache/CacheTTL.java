package com.firedemo.demo.infrastructure.cache;

import java.time.Duration;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 缓存 TTL 工具 — 随机抖动防雪崩
 * <p>
 * 所有缓存写入必须统一使用此工具生成 TTL，
 * 避免大量 key 在同一秒集中过期导致 DB 瞬时高负载。
 */
public final class CacheTTL {

    private CacheTTL() {
    }

    /** Dashboard 指标数据，基础 30 分钟，随机 ±5 分钟抖动 */
    public static Duration dashboardMetrics() {
        int jitterSeconds = ThreadLocalRandom.current().nextInt(300);
        return Duration.ofMinutes(30).plusSeconds(jitterSeconds);
    }

    /** 成绩分布，基础 30 分钟，随机 ±5 分钟 */
    public static Duration scoreDistribution() {
        return dashboardMetrics();
    }

    /** 知识点掌握度，基础 30 分钟，随机 ±5 分钟 */
    public static Duration knowledgeMastery() {
        return dashboardMetrics();
    }

    /** 高频错题，基础 20 分钟，随机 ±3 分钟 */
    public static Duration frequentErrors() {
        int jitterSeconds = ThreadLocalRandom.current().nextInt(180);
        return Duration.ofMinutes(20).plusSeconds(jitterSeconds);
    }

    /** 班级列表，基础 2 小时，随机 ±10 分钟 */
    public static Duration classList() {
        int jitterSeconds = ThreadLocalRandom.current().nextInt(600);
        return Duration.ofHours(2).plusSeconds(jitterSeconds);
    }

    /** 通用基础 TTL，随机 ±5 分钟 */
    public static Duration ofMinutes(long minutes) {
        int jitterSeconds = ThreadLocalRandom.current().nextInt(300);
        return Duration.ofMinutes(minutes).plusSeconds(jitterSeconds);
    }
}
