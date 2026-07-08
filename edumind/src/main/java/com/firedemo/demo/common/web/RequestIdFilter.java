package com.firedemo.demo.common.web;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;

/**
 * 请求追踪过滤器 — 为每个 HTTP 请求生成/传播 RequestId。
 * <p>
 * 优先级最高（{@code Ordered.HIGHEST_PRECEDENCE}），确保在其他 Filter / Interceptor
 * 执行前将 requestId 注入 MDC，使所有日志自动携带追踪 ID。
 * </p>
 *
 * <h3>行为</h3>
 * <ul>
 *   <li>从上游请求头 {@code X-Request-Id} 读取已有 ID（Nginx 或调用方传入）</li>
 *   <li>若无，生成 UUID v4 作为本次请求 ID</li>
 *   <li>写入 MDC ({@code requestId})，供 logback 模式使用</li>
 *   <li>设置响应头 {@code X-Request-Id}，前端/调用方可据此上报问题</li>
 *   <li>请求完成后自动清理 MDC</li>
 * </ul>
 */
@Slf4j
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class RequestIdFilter extends OncePerRequestFilter {

    /** 上游传递的 Request ID header */
    private static final String REQUEST_ID_HEADER = "X-Request-Id";

    /** 内部 MDC key，对应 logback 模式中的 %X{requestId} */
    public static final String MDC_REQUEST_ID = "requestId";

    /** 用于跨服务传播的响应头 */
    private static final String RESPONSE_REQUEST_ID_HEADER = "X-Request-Id";

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request,
                                    @NonNull HttpServletResponse response,
                                    @NonNull FilterChain filterChain)
            throws ServletException, IOException {

        // 1. 获取或生成 RequestId
        String requestId = request.getHeader(REQUEST_ID_HEADER);
        if (!StringUtils.hasText(requestId)) {
            requestId = UUID.randomUUID().toString().replace("-", "");
        }

        // 2. 注入 MDC — 后续所有日志自动携带
        MDC.put(MDC_REQUEST_ID, requestId);

        // 3. 响应头中返回 requestId，便于前端/调用方问题定位
        response.setHeader(RESPONSE_REQUEST_ID_HEADER, requestId);

        try {
            filterChain.doFilter(request, response);
        } finally {
            // 4. 清理 MDC，防止线程池复用时数据残留
            MDC.remove(MDC_REQUEST_ID);
        }
    }

    @Override
    protected boolean shouldNotFilter(@NonNull HttpServletRequest request) {
        // 不追踪 Actuator 健康检查（减少噪音）
        String path = request.getRequestURI();
        return path.startsWith("/actuator/health") || path.startsWith("/actuator/liveness");
    }
}
