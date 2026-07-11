package com.firedemo.demo.common.util;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * AES-256-GCM 加解密工具 — 用于 PII 敏感字段保护。
 * <p>
 * 密钥从 {@code app.encrypt.aes-key} 配置读取，生产环境通过环境变量注入。
 * GCM 模式自带认证，可检测密文是否被篡改。
 */
@Slf4j
@Component
public class AesUtil {

    private static final String ALGORITHM = "AES";
    private static final String TRANSFORMATION = "AES/GCM/NoPadding";
    private static final int GCM_IV_LENGTH = 12;   // 96 bits
    private static final int GCM_TAG_LENGTH = 128; // bits

    private final SecretKeySpec keySpec;

    public AesUtil(@Value("${app.encrypt.aes-key}") String base64Key) {
        if (base64Key == null || base64Key.length() < 32) {
            throw new IllegalStateException(
                    "app.encrypt.aes-key 未配置或长度不足（至少需要 32 字节 Base64）。" +
                    "生成命令：openssl rand -base64 32");
        }
        byte[] keyBytes;
        try {
            keyBytes = Base64.getDecoder().decode(base64Key);
        } catch (IllegalArgumentException e) {
            throw new IllegalStateException(
                    "app.encrypt.aes-key 不是有效的 Base64 字符串。" +
                    "请用 openssl rand -base64 32 生成，并将结果设置到 ENCRYPT_AES_KEY 环境变量", e);
        }
        this.keySpec = new SecretKeySpec(keyBytes, ALGORITHM);
        log.info("AES-256-GCM 加密模块已初始化");
    }

    /**
     * 加密明文，返回 Base64 编码的密文（含 IV）。
     * 格式：{12 字节 IV}{密文 + 16 字节 GCM Tag} → Base64
     */
    public String encrypt(String plainText) {
        if (plainText == null) return null;
        try {
            byte[] iv = new byte[GCM_IV_LENGTH];
            new SecureRandom().nextBytes(iv);
            GCMParameterSpec spec = new GCMParameterSpec(GCM_TAG_LENGTH, iv);

            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.ENCRYPT_MODE, keySpec, spec);
            byte[] encrypted = cipher.doFinal(plainText.getBytes(StandardCharsets.UTF_8));

            // IV + 密文
            byte[] combined = new byte[iv.length + encrypted.length];
            System.arraycopy(iv, 0, combined, 0, iv.length);
            System.arraycopy(encrypted, 0, combined, iv.length, encrypted.length);

            return Base64.getEncoder().encodeToString(combined);
        } catch (Exception e) {
            log.error("AES 加密失败", e);
            throw new RuntimeException("加密失败", e);
        }
    }

    /**
     * 解密 Base64 密文，返回明文。
     */
    public String decrypt(String cipherText) {
        if (cipherText == null) return null;
        try {
            byte[] combined = Base64.getDecoder().decode(cipherText);

            byte[] iv = new byte[GCM_IV_LENGTH];
            byte[] encrypted = new byte[combined.length - GCM_IV_LENGTH];
            System.arraycopy(combined, 0, iv, 0, GCM_IV_LENGTH);
            System.arraycopy(combined, GCM_IV_LENGTH, encrypted, 0, encrypted.length);

            GCMParameterSpec spec = new GCMParameterSpec(GCM_TAG_LENGTH, iv);
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.DECRYPT_MODE, keySpec, spec);
            byte[] decrypted = cipher.doFinal(encrypted);

            return new String(decrypted, StandardCharsets.UTF_8);
        } catch (Exception e) {
            log.error("AES 解密失败", e);
            throw new RuntimeException("解密失败", e);
        }
    }
}
