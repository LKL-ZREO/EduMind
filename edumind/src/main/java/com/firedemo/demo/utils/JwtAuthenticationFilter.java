package com.firedemo.demo.utils;

import com.firedemo.demo.Service.TokenService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final String COOKIE_NAME = "edumind_token";

    @Autowired
    private JwtUtil jwtUtil;

    @Autowired
    private TokenService tokenService;

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {

        String path = request.getRequestURI();

        // 放行 OPTIONS 预检请求
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            filterChain.doFilter(request, response);
            return;
        }

        // 放行路径（不需要 JWT）
        if (path.startsWith("/api/auth/") || path.startsWith("/api/chat/health")
                || path.startsWith("/api/onebot/rag") || path.startsWith("/api/homework/")
                || path.startsWith("/mcp")
                || path.startsWith("/v3/api-docs") || path.startsWith("/swagger-ui")
                || path.equals("/doc.html") || path.startsWith("/webjars")
                || path.startsWith("/actuator") || path.equals("/error") || path.equals("/favicon.ico")) {
            filterChain.doFilter(request, response);
            return;
        }

        // 提取 token：优先 Authorization Header，其次 HttpOnly Cookie
        String token = extractToken(request);

        if (token == null) {
            log.warn("JWT认证失败: Token 缺失, path={}", path);
            sendUnauthorized(response, "缺少登录凭证");
            return;
        }

        // 检查黑名单
        if (tokenService.isBlacklisted(token)) {
            log.warn("JWT认证失败: Token 已吊销, path={}", path);
            sendUnauthorized(response, "登录凭证已失效，请重新登录");
            return;
        }

        // 验证 token
        if (!jwtUtil.validateToken(token)) {
            log.warn("JWT认证失败: Token无效或已过期, path={}", path);
            sendUnauthorized(response, "登录已过期，请重新登录");
            return;
        }

        // 设置 Spring Security 上下文
        Long userId = jwtUtil.getUserIdFromToken(token);
        String username = jwtUtil.getUsernameFromToken(token);
        String role = jwtUtil.getRoleFromToken(token);

        MDC.put("userId", String.valueOf(userId));
        MDC.put("username", username);

        try {
            String authority = (role != null) ? "ROLE_" + role : "ROLE_TEACHER";
            List<SimpleGrantedAuthority> authorities = List.of(new SimpleGrantedAuthority(authority));

            UsernamePasswordAuthenticationToken authentication =
                    new UsernamePasswordAuthenticationToken(username, null, authorities);
            authentication.setDetails(userId);
            SecurityContextHolder.getContext().setAuthentication(authentication);

            request.setAttribute("userId", userId);
            Integer status = jwtUtil.getStatusFromToken(token);
            request.setAttribute("status", status);

            filterChain.doFilter(request, response);
        } finally {
            MDC.remove("userId");
            MDC.remove("username");
        }
    }

    /** 从 Cookie 或 Authorization Header 提取 token */
    private String extractToken(HttpServletRequest request) {
        // 1. Authorization Header（前端 Axios 用）
        String header = request.getHeader("Authorization");
        if (header != null && header.startsWith("Bearer ") && header.length() > 7) {
            return header.substring(7);
        }
        // 2. HttpOnly Cookie（更安全的方式）
        Cookie[] cookies = request.getCookies();
        if (cookies != null) {
            return Arrays.stream(cookies)
                    .filter(c -> COOKIE_NAME.equals(c.getName()))
                    .map(Cookie::getValue)
                    .findFirst()
                    .orElse(null);
        }
        return null;
    }

    private void sendUnauthorized(HttpServletResponse response, String message) throws IOException {
        response.setStatus(401);
        response.setContentType("application/json;charset=UTF-8");
        response.getWriter().write("{\"code\":401,\"message\":\"" + message + "\"}");
    }
}
