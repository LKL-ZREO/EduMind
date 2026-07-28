package com.firedemo.demo.utils;

import com.firedemo.demo.Entity.User;
import com.firedemo.demo.Service.UserService;
import com.firedemo.demo.Service.UserSessionService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import org.slf4j.MDC;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/** Restores application request attributes and rejects disabled teacher sessions. */
@Component
@RequiredArgsConstructor
public class TeacherSessionContextFilter extends OncePerRequestFilter {

    private final UserService userService;
    private final UserSessionService userSessionService;

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (!isTeacher(authentication)) {
            filterChain.doFilter(request, response);
            return;
        }

        Long userId = authentication.getDetails() instanceof Long id ? id : null;
        User user = userId != null ? userService.getById(userId) : null;
        if (user == null || Integer.valueOf(0).equals(user.getStatus())) {
            userSessionService.revokeByUsername(authentication.getName());
            invalidate(request.getSession(false));
            SecurityContextHolder.clearContext();
            unauthorized(response);
            return;
        }

        request.setAttribute("userId", userId);
        request.setAttribute("status", user.getStatus());
        MDC.put("userId", String.valueOf(userId));
        MDC.put("username", authentication.getName());
        try {
            filterChain.doFilter(request, response);
        } finally {
            MDC.remove("userId");
            MDC.remove("username");
        }
    }

    private boolean isTeacher(Authentication authentication) {
        return authentication != null
                && authentication.isAuthenticated()
                && authentication.getAuthorities().stream()
                .anyMatch(authority -> "ROLE_TEACHER".equals(authority.getAuthority()));
    }

    private void invalidate(HttpSession session) {
        if (session == null) return;
        try {
            session.invalidate();
        } catch (IllegalStateException ignored) {
            // Already invalidated by Spring Session revocation.
        }
    }

    private void unauthorized(HttpServletResponse response) throws IOException {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType("application/json;charset=UTF-8");
        response.getWriter().write("{\"code\":401,\"message\":\"账号已被禁用或不存在\"}");
    }
}
