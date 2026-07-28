package com.firedemo.demo.common.aspect;

import com.firedemo.demo.common.annotation.RateLimit;
import com.firedemo.demo.common.exception.BusinessException;
import com.firedemo.demo.common.exception.ErrorCode;
import jakarta.annotation.PostConstruct;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.redisson.api.RScript;
import org.redisson.api.RedissonClient;
import org.redisson.client.codec.StringCodec;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Slf4j
@Aspect
@Component
@RequiredArgsConstructor
public class RateLimitAspect {

    private final RedissonClient redissonClient;

    private static final String LUA_SCRIPT;
    private String luaScriptSha;

    static {
        try {
            ClassPathResource resource = new ClassPathResource("scripts/rate_limit.lua");
            LUA_SCRIPT = new String(resource.getContentAsByteArray(), StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new RuntimeException("Failed to load rate limit Lua script", e);
        }
    }

    @PostConstruct
    public void init() {
        try {
            this.luaScriptSha = redissonClient.getScript(StringCodec.INSTANCE).scriptLoad(LUA_SCRIPT);
            log.info("Rate limit Lua script preloaded, SHA1={}", luaScriptSha);
        } catch (RuntimeException e) {
            this.luaScriptSha = null;
            log.warn("Rate limit Lua preload failed, rate limit aspect will fail open. error={}", e.getMessage());
        }
    }

    @Around("@annotation(rateLimit)")
    public Object around(ProceedingJoinPoint joinPoint, RateLimit rateLimit) throws Throwable {
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        Method method = signature.getMethod();
        String className = method.getDeclaringClass().getSimpleName();
        String methodName = method.getName();

        if (luaScriptSha == null) {
            return joinPoint.proceed();
        }

        long intervalMs = calculateIntervalMs(rateLimit.interval(), rateLimit.timeUnit());
        List<String> keys = generateKeys(className, methodName, rateLimit.dimensions());
        Object[] scriptArgs = {
                String.valueOf(System.currentTimeMillis()),
                String.valueOf(1),
                String.valueOf(intervalMs),
                String.valueOf((long) rateLimit.count()),
                UUID.randomUUID().toString()
        };

        for (String key : keys) {
            Long result;
            try {
                result = toLong(redissonClient.getScript(StringCodec.INSTANCE)
                        .evalSha(RScript.Mode.READ_WRITE, luaScriptSha, RScript.ReturnType.VALUE,
                                List.of(key), scriptArgs));
            } catch (RuntimeException e) {
                log.warn("Rate limit check failed, fail open. key={}, method={}.{}, error={}",
                        key, className, methodName, e.getMessage());
                return joinPoint.proceed();
            }

            if (result == null || result == 0) {
                log.warn("Rate limit exceeded: key={}, method={}.{}", key, className, methodName);
                return handleRateLimitExceeded(joinPoint, rateLimit);
            }
        }

        return joinPoint.proceed();
    }

    private Object handleRateLimitExceeded(ProceedingJoinPoint joinPoint, RateLimit rateLimit) throws Throwable {
        if (!rateLimit.fallback().isEmpty()) {
            Class<?> targetClass = joinPoint.getTarget().getClass();
            MethodSignature signature = (MethodSignature) joinPoint.getSignature();
            try {
                Method fallback = targetClass.getDeclaredMethod(rateLimit.fallback(), signature.getParameterTypes());
                fallback.setAccessible(true);
                return fallback.invoke(joinPoint.getTarget(), joinPoint.getArgs());
            } catch (NoSuchMethodException e1) {
                try {
                    Method fallback = targetClass.getDeclaredMethod(rateLimit.fallback());
                    fallback.setAccessible(true);
                    return fallback.invoke(joinPoint.getTarget());
                } catch (NoSuchMethodException ignored) {
                    log.warn("Rate limit fallback method not found: {}", rateLimit.fallback());
                }
            }
        }
        throw new BusinessException(ErrorCode.RATE_LIMIT_EXCEEDED);
    }

    private List<String> generateKeys(String className, String methodName, RateLimit.Dimension[] dimensions) {
        String hashTag = "{" + className + ":" + methodName + "}";
        String prefix = "ratelimit:" + hashTag;
        List<String> keys = new ArrayList<>();
        for (RateLimit.Dimension dim : dimensions) {
            keys.add(switch (dim) {
                case GLOBAL -> prefix + ":global";
                case IP -> prefix + ":ip:" + getClientIp();
                case USER -> prefix + ":user:" + getCurrentUserId();
            });
        }
        return keys;
    }

    private String getClientIp() {
        ServletRequestAttributes attrs = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        if (attrs == null) return "unknown";
        HttpServletRequest req = attrs.getRequest();
        String ip = req.getHeader("X-Forwarded-For");
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = req.getHeader("X-Real-IP");
        }
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = req.getRemoteAddr();
        }
        if (ip != null && ip.contains(",")) {
            ip = ip.split(",")[0].trim();
        }
        return ip != null ? ip : "unknown";
    }

    private String getCurrentUserId() {
        ServletRequestAttributes attrs = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        if (attrs == null) return "anonymous";
        Object userId = attrs.getRequest().getAttribute("userId");
        if (userId != null) return userId.toString();
        userId = attrs.getRequest().getHeader("X-User-Id");
        return userId != null ? userId.toString() : "anonymous";
    }

    private long calculateIntervalMs(long interval, RateLimit.TimeUnit unit) {
        return switch (unit) {
            case MILLISECONDS -> interval;
            case SECONDS -> interval * 1000;
            case MINUTES -> interval * 60 * 1000;
            case HOURS -> interval * 3600 * 1000;
            case DAYS -> interval * 86400 * 1000;
        };
    }

    private Long toLong(Object obj) {
        if (obj == null) return null;
        if (obj instanceof Long l) return l;
        if (obj instanceof Integer i) return i.longValue();
        if (obj instanceof String s) {
            try {
                return Long.parseLong(s);
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return null;
    }
}
