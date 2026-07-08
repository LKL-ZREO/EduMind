package com.firedemo.demo.utils;

import com.firedemo.demo.common.web.RequestIdFilter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Collections;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {



    @Autowired
    private JwtUtil jwtUtil;

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {

        String path = request.getRequestURI();
        log.debug("JWT过滤器处理: {} {}", request.getMethod(), path);

        // 放行 OPTIONS 预检请求
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            filterChain.doFilter(request, response);
            return;
        }

         //放行路径（不需要 JWT）
        if (path.startsWith("/api/auth/") || path.startsWith("/api/chat/health")
                || path.startsWith("/api/onebot/rag") || path.startsWith("/api/homework/")
                || path.startsWith("/mcp")
                || path.startsWith("/v3/api-docs") || path.startsWith("/swagger-ui")
                || path.equals("/doc.html") || path.startsWith("/webjars")
                || path.startsWith("/actuator") || path.equals("/error") || path.equals("/favicon.ico")) {
            filterChain.doFilter(request, response);
            return;
        }

        // 获取 token（只从 Header）
        String header = request.getHeader("Authorization");

        if (header == null || !header.startsWith("Bearer ") || header.length() <= 7) {
            log.warn("JWT认证失败: Authorization header 缺失或格式错误, path={}", path);
            response.setStatus(401);
            response.setContentType("application/json;charset=UTF-8");
            response.getWriter().write("{\"code\":401,\"message\":\"缺少 Token\"}");
            return;
        }

        String token = header.substring(7);

        // 验证 token
        boolean isValid = jwtUtil.validateToken(token);
        if (!isValid) {
            log.warn("JWT认证失败: Token无效或已过期, path={}", path);
            response.setStatus(401);
            response.setContentType("application/json;charset=UTF-8");
            response.getWriter().write("{\"code\":401,\"message\":\"Token 无效或已过期\"}");
            return;
        }

        // 设置 Spring Security 上下文
        Long userId = jwtUtil.getUserIdFromToken(token);
        String username = jwtUtil.getUsernameFromToken(token);

        // 将用户信息注入 MDC，后续所有日志自动携带 userId 和 username
        MDC.put("userId", String.valueOf(userId));
        MDC.put("username", username);

        try {
            UsernamePasswordAuthenticationToken authentication =
                    new UsernamePasswordAuthenticationToken(
                            username, null, Collections.emptyList());
            authentication.setDetails(userId);
            SecurityContextHolder.getContext().setAuthentication(authentication);

            // 设置用户上下文
            request.setAttribute("userId", userId);

            // 设置 status 到上下文（status=1用main，status=2用jarvis）
            Integer status = jwtUtil.getStatusFromToken(token);
            request.setAttribute("status", status);
            log.debug("用户上下文已注入 MDC: userId={}, status={}", userId, status);

            filterChain.doFilter(request, response);
        } finally {
            MDC.remove("userId");
            MDC.remove("username");
        }
    }
}
