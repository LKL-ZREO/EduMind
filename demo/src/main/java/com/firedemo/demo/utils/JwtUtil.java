package com.firedemo.demo.utils;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;

@Component
public class JwtUtil {

    private static final long EXPIRATION = 86400000; // 24小时

    private final SecretKey key;

    public JwtUtil(@Value("${jwt.secret}") String secret) {
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    }

    // 生成 Token
    public String generateToken(Long userId, String username) {
        return generateToken(userId, username, null);
    }

    // 生成 Token（带 status）
    public String generateToken(Long userId, String username, Integer status) {
        Date now = new Date();
        Date expiry = new Date(now.getTime() + EXPIRATION);

        var builder = Jwts.builder()
                .setSubject(String.valueOf(userId))
                .claim("username", username)
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

    // 从 Token 获取用户名
    public String getUsernameFromToken(String token) {
        Claims claims = parseToken(token);
        return claims.get("username", String.class);
    }

    // 验证 Token 是否有效
    public boolean validateToken(String token) {
        try {
            parseToken(token);
            return true;
        } catch (Exception e) {
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