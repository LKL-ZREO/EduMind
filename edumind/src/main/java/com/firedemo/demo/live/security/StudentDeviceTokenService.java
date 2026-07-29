package com.firedemo.demo.live.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.Optional;

/** Remembers a student identity on a personal device across live classroom sessions. */
@Slf4j
@Service
public class StudentDeviceTokenService {

    public static final String COOKIE_NAME = "edumind_student_device";
    private static final String TOKEN_TYPE = "student-device";

    private final SecretKey key;
    private final Duration ttl;
    private final boolean secureCookies;

    public StudentDeviceTokenService(
            @Value("${edumind.live.token-secret}") String secret,
            @Value("${edumind.live.device-token-ttl:P180D}") Duration ttl,
            @Value("${server.servlet.session.cookie.secure:false}") boolean secureCookies) {
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.ttl = ttl;
        this.secureCookies = secureCookies;
    }

    public String issue(String studentId) {
        Instant now = Instant.now();
        return Jwts.builder()
                .setSubject(studentId)
                .claim("type", TOKEN_TYPE)
                .setIssuedAt(Date.from(now))
                .setExpiration(Date.from(now.plus(ttl)))
                .signWith(key, SignatureAlgorithm.HS256)
                .compact();
    }

    public Optional<String> parse(String token) {
        if (token == null || token.isBlank()) return Optional.empty();
        try {
            Claims claims = Jwts.parserBuilder()
                    .setSigningKey(key)
                    .build()
                    .parseClaimsJws(token)
                    .getBody();
            String studentId = claims.getSubject();
            if (!TOKEN_TYPE.equals(claims.get("type", String.class))
                    || studentId == null || studentId.isBlank()) {
                return Optional.empty();
            }
            return Optional.of(studentId);
        } catch (RuntimeException e) {
            log.debug("Invalid student device token: {}", e.getClass().getSimpleName());
            return Optional.empty();
        }
    }

    public ResponseCookie bindingCookie(String studentId) {
        return cookie(issue(studentId), ttl);
    }

    public ResponseCookie clearingCookie() {
        return cookie("", Duration.ZERO);
    }

    private ResponseCookie cookie(String value, Duration maxAge) {
        return ResponseCookie.from(COOKIE_NAME, value)
                .httpOnly(true)
                .secure(secureCookies)
                .sameSite("Lax")
                .path("/")
                .maxAge(maxAge)
                .build();
    }
}
