package com.firedemo.edumind.platform.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.firedemo.edumind.shared.exception.ErrorCode;
import com.firedemo.edumind.live.security.StudentTokenAuthenticationFilter;
import com.firedemo.edumind.integration.mcp.security.McpApiKeyFilter;
import com.firedemo.edumind.auth.TeacherSessionContextFilter;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.context.DelegatingSecurityContextRepository;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.RequestAttributeSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfException;
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.Arrays;
import java.util.Map;

/** Security configuration for teacher sessions, classroom tokens, MCP, and CSRF. */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final StudentTokenAuthenticationFilter studentTokenFilter;
    private final TeacherSessionContextFilter teacherSessionContextFilter;
    private final McpApiKeyFilter mcpApiKeyFilter;
    private final ObjectMapper objectMapper;

    @Value("${app.cors.allowed-origins:http://localhost:5173,http://127.0.0.1:5173}")
    private String allowedOrigins;

    @Value("${app.cors.allowed-origin-patterns:http://localhost:*,http://127.0.0.1:*}")
    private String allowedOriginPatterns;

    @Value("${server.servlet.session.cookie.secure:false}")
    private boolean secureCookies;

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http,
                                           SecurityContextRepository securityContextRepository,
                                           CookieCsrfTokenRepository csrfTokenRepository) throws Exception {
        CsrfTokenRequestAttributeHandler csrfRequestHandler = new CsrfTokenRequestAttributeHandler();
        csrfRequestHandler.setCsrfRequestAttributeName("_csrf");

        http
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                .csrf(csrf -> csrf
                        .csrfTokenRepository(csrfTokenRepository)
                        .csrfTokenRequestHandler(csrfRequestHandler)
                        .ignoringRequestMatchers("/mcp", "/mcp/**", "/api/onebot/rag"))
                .httpBasic(basic -> basic.disable())
                .formLogin(form -> form.disable())
                .logout(logout -> logout.disable())
                .requestCache(cache -> cache.disable())
                .sessionManagement(session -> session
                        .sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED))
                .headers(headers -> headers
                        .contentSecurityPolicy(csp -> csp.policyDirectives(
                                "default-src 'self'; script-src 'self' 'unsafe-inline'; "
                                        + "style-src 'self' 'unsafe-inline'; img-src 'self' data: blob:"))
                        .frameOptions(frame -> frame.deny())
                        .xssProtection(xss -> xss.headerValue(
                                org.springframework.security.web.header.writers.XXssProtectionHeaderWriter
                                        .HeaderValue.ENABLED_MODE_BLOCK))
                        .httpStrictTransportSecurity(hsts -> hsts
                                .includeSubDomains(true)
                                .maxAgeInSeconds(31536000)))
                .addFilterBefore(mcpApiKeyFilter, UsernamePasswordAuthenticationFilter.class)
                .addFilterBefore(studentTokenFilter, UsernamePasswordAuthenticationFilter.class)
                .addFilterAfter(teacherSessionContextFilter, StudentTokenAuthenticationFilter.class)
                .exceptionHandling(exception -> exception
                        .authenticationEntryPoint((request, response, error) -> {
                            response.setStatus(401);
                            response.setContentType("application/json;charset=UTF-8");
                            response.getWriter().write(objectMapper.writeValueAsString(Map.of(
                                    "code", ErrorCode.UNAUTHORIZED.getCode(),
                                    "message", ErrorCode.UNAUTHORIZED.getMessage())));
                        })
                        .accessDeniedHandler(accessDeniedHandler()))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(HttpMethod.GET, "/api/auth/csrf").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/auth/login", "/api/auth/register").permitAll()
                        .requestMatchers("/api/auth/me", "/api/auth/logout").authenticated()
                        .requestMatchers(
                                "/api/chat/health",
                                "/api/onebot/rag",
                                "/api/teacher/classes/join",
                                "/ws/live",
                                "/ws/live/**",
                                "/v3/api-docs/**",
                                "/swagger-ui/**",
                                "/swagger-ui.html",
                                "/doc.html",
                                "/webjars/**",
                                "/actuator/health/**",
                                "/actuator/prometheus").permitAll()
                        .requestMatchers(HttpMethod.GET,
                                "/api/homework/classes",
                                "/api/homework/tasks",
                                "/api/homework/submit-status",
                                "/api/homework/result/**",
                                "/api/homework/check-qq-binding",
                                "/api/live/session/*",
                                "/api/preview/*").permitAll()
                        .requestMatchers(HttpMethod.POST,
                                "/api/homework/submit",
                                "/api/homework/bind-qq",
                                "/api/live/join",
                                "/api/live/quick-join",
                                "/api/live/device/unbind").permitAll()
                        .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                        .requestMatchers("/error").permitAll()
                        .anyRequest().authenticated())
                .securityContext(context -> context
                        .securityContextRepository(securityContextRepository)
                        .requireExplicitSave(true));

        return http.build();
    }

    @Bean
    public SecurityContextRepository securityContextRepository() {
        return new DelegatingSecurityContextRepository(
                new RequestAttributeSecurityContextRepository(),
                new HttpSessionSecurityContextRepository());
    }

    @Bean
    public FilterRegistrationBean<StudentTokenAuthenticationFilter> studentTokenFilterRegistration() {
        return securityChainOnly(studentTokenFilter);
    }

    @Bean
    public FilterRegistrationBean<TeacherSessionContextFilter> teacherSessionContextFilterRegistration() {
        return securityChainOnly(teacherSessionContextFilter);
    }

    @Bean
    public FilterRegistrationBean<McpApiKeyFilter> mcpApiKeyFilterRegistration() {
        return securityChainOnly(mcpApiKeyFilter);
    }

    @Bean
    public CookieCsrfTokenRepository csrfTokenRepository() {
        CookieCsrfTokenRepository repository = CookieCsrfTokenRepository.withHttpOnlyFalse();
        repository.setCookieName("XSRF-TOKEN");
        repository.setHeaderName("X-XSRF-TOKEN");
        repository.setCookiePath("/");
        repository.setCookieCustomizer(cookie -> cookie
                .httpOnly(false)
                .secure(secureCookies)
                .sameSite("Lax")
                .path("/"));
        return repository;
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(splitConfig(allowedOrigins));
        if (allowedOriginPatterns != null && !allowedOriginPatterns.isBlank()) {
            configuration.setAllowedOriginPatterns(splitConfig(allowedOriginPatterns));
        }
        configuration.setAllowedMethods(Arrays.asList(
                "GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(Arrays.asList(
                "Authorization", "Content-Type", "X-XSRF-TOKEN", "X-Session-Id", "X-MCP-API-Key"));
        configuration.setAllowCredentials(true);
        configuration.setExposedHeaders(java.util.List.of("X-Request-Id"));
        configuration.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }

    private AccessDeniedHandler accessDeniedHandler() {
        return (request, response, exception) -> {
            boolean csrfFailure = exception instanceof CsrfException;
            response.setStatus(403);
            response.setContentType("application/json;charset=UTF-8");
            response.getWriter().write(objectMapper.writeValueAsString(Map.of(
                    "code", csrfFailure ? 40301 : ErrorCode.FORBIDDEN.getCode(),
                    "message", csrfFailure
                            ? "CSRF token 无效或已过期"
                            : ErrorCode.FORBIDDEN.getMessage())));
        };
    }

    private java.util.List<String> splitConfig(String value) {
        if (value == null || value.isBlank()) return java.util.List.of();
        return Arrays.stream(value.split(","))
                .map(String::trim)
                .filter(origin -> !origin.isEmpty())
                .toList();
    }

    private <T extends jakarta.servlet.Filter> FilterRegistrationBean<T> securityChainOnly(T filter) {
        FilterRegistrationBean<T> registration = new FilterRegistrationBean<>(filter);
        registration.setEnabled(false);
        return registration;
    }
}
