package com.firedemo.demo.live.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

/** Authenticates only the short-lived Bearer token returned by the live join endpoint. */
@Component
@RequiredArgsConstructor
public class StudentTokenAuthenticationFilter extends OncePerRequestFilter {

    private final LiveSessionTokenService tokenService;

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String token = bearerToken(request);
        if (token == null) {
            filterChain.doFilter(request, response);
            return;
        }

        var principal = tokenService.parse(token);
        if (principal.isEmpty()) {
            unauthorized(response, "课堂凭证无效或已过期");
            return;
        }

        ClassroomStudentPrincipal student = principal.get();
        UsernamePasswordAuthenticationToken authentication =
                new UsernamePasswordAuthenticationToken(
                        student,
                        null,
                        List.of(new SimpleGrantedAuthority("ROLE_STUDENT")));
        authentication.setDetails(student.studentId());
        SecurityContextHolder.getContext().setAuthentication(authentication);
        request.setAttribute("subject", student.studentId());
        request.setAttribute("sessionId", student.liveSessionId());

        filterChain.doFilter(request, response);
    }

    private String bearerToken(HttpServletRequest request) {
        String header = request.getHeader("Authorization");
        return header != null && header.startsWith("Bearer ") && header.length() > 7
                ? header.substring(7)
                : null;
    }

    private void unauthorized(HttpServletResponse response, String message) throws IOException {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType("application/json;charset=UTF-8");
        response.getWriter().write("{\"code\":401,\"message\":\"" + message + "\"}");
    }
}
