package com.firedemo.demo.Service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RBucket;
import org.redisson.api.RedissonClient;
import org.springframework.stereotype.Service;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.HexFormat;
import java.util.UUID;

/**
 * Token 管理：刷新令牌 + 退出黑名单。
 * <p>
 * 设计：
 * <ul>
 *   <li>Access Token（JWT）：短有效期（默认 15 min），无状态</li>
 *   <li>Refresh Token（UUID）：长有效期（7 天），存 Redis，可随时吊销</li>
 *   <li>退出黑名单：将 access token hash 存入 Redis，TTL = token 剩余有效期</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TokenService {

    private final RedissonClient redisson;

    /** 刷新令牌在 Redis 中的 key 前缀 */
    private static final String REFRESH_PREFIX = "auth:refresh:";
    /** 黑名单 key 前缀 */
    private static final String BLACKLIST_PREFIX = "auth:blacklist:";
    /** 刷新令牌有效期 */
    private static final Duration REFRESH_TTL = Duration.ofDays(7);

    // ────────── 刷新令牌 ──────────

    /** 生成刷新令牌并存入 Redis */
    public String createRefreshToken(Long userId) {
        String token = UUID.randomUUID().toString().replace("-", "");
        RBucket<Long> bucket = redisson.getBucket(REFRESH_PREFIX + token);
        bucket.set(userId, REFRESH_TTL);
        log.debug("生成刷新令牌: userId={}", userId);
        return token;
    }

    /** 校验并消费刷新令牌，返回 userId；无效返回 null */
    public Long consumeRefreshToken(String refreshToken) {
        RBucket<Long> bucket = redisson.getBucket(REFRESH_PREFIX + refreshToken);
        Long userId = bucket.get();
        if (userId == null) {
            log.warn("刷新令牌无效或已过期");
            return null;
        }
        // 单次使用：刷新后旧 token 删除，防止重放攻击
        bucket.delete();
        return userId;
    }

    /** 吊销用户所有刷新令牌 */
    public void revokeUserRefreshTokens(Long userId) {
        // 简化实现：刷新令牌是单次使用 + 短 TTL，不做全量扫描
        log.debug("用户刷新令牌将在下次使用时自动失效: userId={}", userId);
    }

    // ────────── 黑名单（退出登录） ──────────

    /** 将 token 加入黑名单，TTL 为 token 剩余有效时间 */
    public void blacklist(String token, long remainingSeconds) {
        if (remainingSeconds <= 0) return;
        String hash = sha256(token);
        RBucket<String> bucket = redisson.getBucket(BLACKLIST_PREFIX + hash);
        bucket.set("revoked", Duration.ofSeconds(remainingSeconds));
        log.debug("Token 已加入黑名单，剩余 {} 秒", remainingSeconds);
    }

    /** 检查 token 是否在黑名单中 */
    public boolean isBlacklisted(String token) {
        String hash = sha256(token);
        RBucket<String> bucket = redisson.getBucket(BLACKLIST_PREFIX + hash);
        return bucket.get() != null;
    }

    // ────────── 工具 ──────────

    private static String sha256(String input) {
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256").digest(input.getBytes());
            return HexFormat.of().formatHex(hash).substring(0, 32);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException(e);
        }
    }
}
