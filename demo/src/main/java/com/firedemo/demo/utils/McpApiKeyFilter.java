package com.firedemo.demo.utils;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.List;

/**
 * MCP 专用 API Key 认证过滤器。
 * <p>
 * /mcp 由 OpenClaw Gateway 调用，不使用普通用户 JWT，而使用固定服务间密钥认证。
 */
@Component
public class McpApiKeyFilter extends OncePerRequestFilter {

    private static final String MCP_PATH_PREFIX = "/mcp";
    private static final String API_KEY_HEADER = "X-MCP-API-Key";

    @Value("${mcp.api-key:}")
    private String mcpApiKey;

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !request.getRequestURI().startsWith(MCP_PATH_PREFIX);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        if (HttpMethod.OPTIONS.matches(request.getMethod())) {
            filterChain.doFilter(request, response);
            return;
        }

        String actual = request.getHeader(API_KEY_HEADER);
        if (!apiKeyMatches(actual)) {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.setContentType("application/json;charset=UTF-8");
            response.getWriter().write("{\"code\":401,\"message\":\"MCP Unauthorized\"}");
            return;
        }

        UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
                "openclaw-mcp",
                null,
                List.of(new SimpleGrantedAuthority("ROLE_MCP"))
        );
        SecurityContextHolder.getContext().setAuthentication(authentication);

        filterChain.doFilter(request, response);
    }

    private boolean apiKeyMatches(String actual) {
        if (mcpApiKey == null || mcpApiKey.isBlank() || actual == null || actual.isBlank()) {
            return false;
        }
        return MessageDigest.isEqual(
                mcpApiKey.getBytes(StandardCharsets.UTF_8),
                actual.getBytes(StandardCharsets.UTF_8)
        );
    }
}
