package com.firedemo.edumind.live.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.Optional;

/** Issues and validates short-lived tokens that grant access to one live classroom. */
@Slf4j
@Service
public class LiveSessionTokenService {

    private static final String TOKEN_TYPE = "live-student";

    private final SecretKey key;
    private final Duration ttl;

    public LiveSessionTokenService(
            @Value("${edumind.live.token-secret}") String secret,
            @Value("${edumind.live.student-token-ttl:PT2H}") Duration ttl) {
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.ttl = ttl;
    }

    public String issue(String studentId, String studentName, Long liveSessionId) {
        Instant now = Instant.now();
        return Jwts.builder()
                .setSubject(studentId)
                .claim("name", studentName)
                .claim("type", TOKEN_TYPE)
                .claim("liveSessionId", liveSessionId)
                .setIssuedAt(Date.from(now))
                .setExpiration(Date.from(now.plus(ttl)))
                .signWith(key, SignatureAlgorithm.HS256)
                .compact();
    }

    public Optional<ClassroomStudentPrincipal> parse(String token) {
        if (token == null || token.isBlank()) {
            return Optional.empty();
        }
        try {
            Claims claims = Jwts.parserBuilder()
                    .setSigningKey(key)
                    .build()
                    .parseClaimsJws(token)
                    .getBody();
            if (!TOKEN_TYPE.equals(claims.get("type", String.class))) {
                return Optional.empty();
            }
            Number sessionId = claims.get("liveSessionId", Number.class);
            if (claims.getSubject() == null || sessionId == null) {
                return Optional.empty();
            }
            return Optional.of(new ClassroomStudentPrincipal(
                    claims.getSubject(),
                    claims.get("name", String.class),
                    sessionId.longValue()));
        } catch (RuntimeException e) {
            log.debug("Invalid live classroom token: {}", e.getClass().getSimpleName());
            return Optional.empty();
        }
    }
}
