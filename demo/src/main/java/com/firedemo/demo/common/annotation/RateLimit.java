package com.firedemo.demo.common.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 限流注解 — 基于 Redis Lua 滑动时间窗口，支持多维度组合限流
 *
 * <pre>
 * {@code
 * @RateLimit(count = 10)                          // 每秒最多10次
 * @RateLimit(dimensions = {GLOBAL, IP}, count = 5) // 全局限流 + IP限流
 * @RateLimit(count = 10, fallback = "fallbackMethod") // 超限调降级方法
 * }
 * </pre>
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface RateLimit {

    enum Dimension { GLOBAL, IP, USER }

    enum TimeUnit { MILLISECONDS, SECONDS, MINUTES, HOURS, DAYS }

    /** 限流维度，默认全局限流 */
    Dimension[] dimensions() default { Dimension.GLOBAL };

    /** 时间窗口内允许的最大请求数 */
    double count();

    /** 时间窗口大小，默认 1 */
    long interval() default 1;

    /** 时间单位，默认秒 */
    TimeUnit timeUnit() default TimeUnit.SECONDS;

    /** 降级方法名（同类的无参/同参方法），为空则抛异常 */
    String fallback() default "";
}
