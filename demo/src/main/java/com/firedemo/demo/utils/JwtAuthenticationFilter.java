package com.firedemo.demo.utils;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Collections;

import static reactor.netty.http.HttpConnectionLiveness.log;

@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {



    @Autowired
    private JwtUtil jwtUtil;

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {

        String path = request.getRequestURI();
        String threadName = Thread.currentThread().getName();
        log.info("[{}] 过滤器处理: {} {}", threadName, request.getMethod(), path);

        // 放行 OPTIONS 预检请求
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            filterChain.doFilter(request, response);
            return;
        }

         //放行路径（不需要 JWT）
        if (path.startsWith("/api/auth/") || path.startsWith("/api/chat/health") || path.startsWith("/api/onebot/rag") || path.startsWith("/api/homework/")) {
            filterChain.doFilter(request, response);
            return;
        }

        // 获取 token（只从 Header）
        String header = request.getHeader("Authorization");
        log.info("[{}] 收到Authorization header: {}", threadName, header != null ? header.substring(0, Math.min(30, header.length())) + "..." : "null");
        
        if (header == null || !header.startsWith("Bearer ") || header.length() <= 7) {
            log.warn("Authorization header 格式错误或缺失");
            response.setStatus(401);
            response.setContentType("application/json;charset=UTF-8");
            response.getWriter().write("{\"code\":401,\"message\":\"缺少 Token\"}");
            return;
        }

        String token = header.substring(7);
        log.info("提取的token: {}", token);

        // 验证 token
        boolean isValid = jwtUtil.validateToken(token);
        log.info("Token验证结果: {}", isValid);
        if (!isValid) {
            log.warn("Token验证失败，token: {}", token.substring(0, Math.min(20, token.length())) + "...");
            response.setStatus(401);
            response.setContentType("application/json;charset=UTF-8");
            response.getWriter().write("{\"code\":401,\"message\":\"Token 无效或已过期\"}");
            return;
        }

        // 设置 Spring Security 上下文
        Long userId = jwtUtil.getUserIdFromToken(token);
        String username = jwtUtil.getUsernameFromToken(token);
        
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
        log.info("用户上下文: userId={}, status={}", userId, status);

        filterChain.doFilter(request, response);
    }
}
