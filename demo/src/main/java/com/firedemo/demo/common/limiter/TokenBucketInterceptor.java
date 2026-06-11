package com.firedemo.demo.common.limiter;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.firedemo.demo.config.BucketConfig;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.web.servlet.HandlerInterceptor;

import java.util.Map;

/**
 * 网关层令牌桶限流拦截器
 * <p>
 * 基于 Bucket4j 分布式令牌桶，按 IP + URI 维度限流。
 * 作用层级：Controller 之前，作为第一道防线。
 * <p>
 * 与 {@link com.firedemo.demo.common.aspect.RateLimitAspect} 分层协作：
 * <pre>
 *   请求 → TokenBucketInterceptor（网关层，Bucket4j 令牌桶）
 *       → RateLimitAspect（方法层，Redis Lua 滑动窗口）
 *       → Controller
 * </pre>
 */
@Slf4j
@RequiredArgsConstructor
public class TokenBucketInterceptor implements HandlerInterceptor {

    private final DistributedRateLimiter rateLimiter;
    private final BucketConfig bucketConfig;
    private final ObjectMapper objectMapper;

    private static final int TOKENS_PER_REQUEST = 1;

    @Override
    public boolean preHandle(HttpServletRequest request,
                             HttpServletResponse response,
                             Object handler) throws Exception {

        String clientIp = getClientIp(request);
        String uri = request.getRequestURI();

        // 构造限流键：{bucket}:{ip}:{uri}
        String bucketKey = buildBucketKey(clientIp, uri);

        // 按接口路径查配置，无匹配则用默认配置
        BucketConfig.Rule rule = resolveRule(uri, request);

        if (rateLimiter.tryConsume(bucketKey, rule.getCapacity(),
                rule.getRefillPerMinute(), TOKENS_PER_REQUEST)) {
            return true;
        }

        log.warn("令牌桶限流触发: key={}, ip={}, uri={}, capacity={}, refill={}/min",
                bucketKey, clientIp, uri, rule.getCapacity(), rule.getRefillPerMinute());

        // 限流拒绝
        response.setStatus(429);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        response.getWriter().write(objectMapper.writeValueAsString(Map.of(
                "code", 429,
                "message", "请求过于频繁，请稍后重试"
        )));
        return false;
    }

    /**
     * 构造限流键
     */
    private String buildBucketKey(String clientIp, String uri) {
        // 使用 {} 包裹以保证同一个 hash slot（Redis Cluster 兼容）
        return "bucket:{" + clientIp + "}:" + uri;
    }

    /**
     * 按 URI 匹配规则，优先级：精确匹配 > 前缀匹配 > 默认
     */
    private BucketConfig.Rule resolveRule(String uri, HttpServletRequest request) {
        Map<String, BucketConfig.Rule> rules = bucketConfig.getRules();

        // 精确匹配
        if (rules.containsKey(uri)) {
            return rules.get(uri);
        }

        // 前缀匹配（如 /api/homework/ 匹配 /api/homework/submit）
        for (Map.Entry<String, BucketConfig.Rule> entry : rules.entrySet()) {
            String pattern = entry.getKey();
            if (pattern.endsWith("/") && uri.startsWith(pattern)) {
                return entry.getValue();
            }
            if (pattern.endsWith("*") && uri.startsWith(pattern.substring(0, pattern.length() - 1))) {
                return entry.getValue();
            }
        }

        // 默认规则：已认证用户给更高配额
        return isAuthenticated(request)
                ? bucketConfig.getAuthenticatedDefault()
                : bucketConfig.getAnonymousDefault();
    }

    private boolean isAuthenticated(HttpServletRequest request) {
        // 通过 JWT Filter 注入的 userId 属性判断
        return request.getAttribute("userId") != null
                || request.getHeader("Authorization") != null;
    }

    private String getClientIp(HttpServletRequest request) {
        String xff = request.getHeader("X-Forwarded-For");
        if (xff != null && !xff.isEmpty() && !"unknown".equalsIgnoreCase(xff)) {
            return xff.split(",")[0].trim();
        }
        String xRealIp = request.getHeader("X-Real-IP");
        if (xRealIp != null && !xRealIp.isEmpty() && !"unknown".equalsIgnoreCase(xRealIp)) {
            return xRealIp;
        }
        return request.getRemoteAddr();
    }
}
