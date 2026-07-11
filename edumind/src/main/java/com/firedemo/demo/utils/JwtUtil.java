package com.firedemo.demo.utils;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.MalformedJwtException;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.UnsupportedJwtException;
import io.jsonwebtoken.security.Keys;
import io.jsonwebtoken.security.SignatureException;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;

@Slf4j
@Component
public class JwtUtil {

    public static final long EXPIRATION = 86400000; // 24小时
    public static final long EXPIRATION_SECONDS = EXPIRATION / 1000;

    private final SecretKey key;

    public JwtUtil(@Value("${jwt.secret}") String secret) {
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    }

    // 生成 Token
    public String generateToken(Long userId, String username) {
        return generateToken(userId, username, null, null);
    }

    // 生成 Token（带 status）
    public String generateToken(Long userId, String username, Integer status) {
        return generateToken(userId, username, status, null);
    }

    // 生成 Token（带 status + role）
    public String generateToken(Long userId, String username, Integer status, String role) {
        Date now = new Date();
        Date expiry = new Date(now.getTime() + EXPIRATION);

        var builder = Jwts.builder()
                .setSubject(String.valueOf(userId))
                .claim("username", username)
                .claim("role", role != null ? role : "TEACHER")
                .setIssuedAt(now)
                .setExpiration(expiry);

        if (status != null) {
            builder.claim("status", status);
        }

        return builder.signWith(key, SignatureAlgorithm.HS256).compact();
    }

    // 从 Token 获取 status
    public Integer getStatusFromToken(String token) {
        Claims claims = parseToken(token);
        return claims.get("status", Integer.class);
    }

    // 解析 Token
    public Claims parseToken(String token) {
        return Jwts.parserBuilder()
                .setSigningKey(key)
                .build()
                .parseClaimsJws(token)
                .getBody();
    }

    // 从 Token 获取用户ID
    public Long getUserIdFromToken(String token) {
        Claims claims = parseToken(token);
        return Long.valueOf(claims.getSubject());
    }

    // 从 Token 获取角色
    public String getRoleFromToken(String token) {
        Claims claims = parseToken(token);
        return claims.get("role", String.class);
    }

    // 从 Token 获取用户名
    public String getUsernameFromToken(String token) {
        Claims claims = parseToken(token);
        return claims.get("username", String.class);
    }

    /** 获取 token 剩余有效秒数（用于黑名单 TTL）；已过期返回 0 */
    public long getRemainingSeconds(String token) {
        try {
            Claims claims = parseToken(token);
            long remaining = (claims.getExpiration().getTime() - System.currentTimeMillis()) / 1000;
            return Math.max(remaining, 0);
        } catch (Exception e) {
            return 0;
        }
    }

    // 验证 Token 是否有效
    public boolean validateToken(String token) {
        try {
            parseToken(token);
            return true;
        } catch (ExpiredJwtException e) {
            log.debug("JWT 已过期: {}", e.getMessage());
            return false;
        } catch (SignatureException e) {
            log.warn("JWT 签名验证失败（可能被篡改）: {}", e.getMessage());
            return false;
        } catch (MalformedJwtException e) {
            log.debug("JWT 格式错误: {}", e.getMessage());
            return false;
        } catch (UnsupportedJwtException e) {
            log.debug("JWT 类型不支持: {}", e.getMessage());
            return false;
        } catch (IllegalArgumentException e) {
            log.debug("JWT 参数为空: {}", e.getMessage());
            return false;
        } catch (Exception e) {
            log.warn("JWT 未知错误: {} - {}", e.getClass().getSimpleName(), e.getMessage());
            return false;
        }
    }

    // 从 request 获取 Token
    public String extractTokenFromRequest(HttpServletRequest request) {
        String header = request.getHeader("Authorization");
        if (header != null && header.startsWith("Bearer ")) {
            return header.substring(7);
        }
        return null;
    }

    // 从 request 获取用户ID
    public Long getUserIdFromRequest(HttpServletRequest request) {
        String token = extractTokenFromRequest(request);
        if (token != null && validateToken(token)) {
            return getUserIdFromToken(token);
        }
        return null;
    }
}