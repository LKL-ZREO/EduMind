package com.firedemo.edumind.platform.web;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/** Converts Redis/session infrastructure outages into a controlled HTTP 503 response. */
@Slf4j
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 1)
public class InfrastructureAvailabilityFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request,
                                    @NonNull HttpServletResponse response,
                                    @NonNull FilterChain filterChain)
            throws ServletException, IOException {
        try {
            filterChain.doFilter(request, response);
        } catch (ServletException e) {
            if (!handleRedisFailure(response, e)) throw e;
        } catch (RuntimeException e) {
            if (!handleRedisFailure(response, e)) throw e;
        }
    }

    private boolean handleRedisFailure(HttpServletResponse response, Throwable error) throws IOException {
        if (!isRedisFailure(error) || response.isCommitted()) {
            return false;
        }
        log.error("Redis-backed session infrastructure is unavailable", error);
        response.resetBuffer();
        response.setStatus(HttpServletResponse.SC_SERVICE_UNAVAILABLE);
        response.setContentType("application/json;charset=UTF-8");
        response.getWriter().write(
                "{\"code\":503,\"message\":\"认证服务暂时不可用，请稍后重试\"}");
        return true;
    }

    private boolean isRedisFailure(Throwable error) {
        Throwable current = error;
        while (current != null) {
            String name = current.getClass().getName();
            if (name.contains("RedisConnectionFailureException")
                    || name.contains("RedisSystemException")
                    || name.contains("RedisCommandTimeoutException")
                    || name.contains("RedisConnectionException")) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }
}
