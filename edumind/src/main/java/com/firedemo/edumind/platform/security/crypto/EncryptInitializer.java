package com.firedemo.edumind.platform.security.crypto;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

/**
 * 启动时将 AesUtil 注入到 AESEncryptHandler，使其能在 MyBatis TypeHandler 中工作。
 */
@Component
@RequiredArgsConstructor
public class EncryptInitializer implements CommandLineRunner {

    private final AesUtil aesUtil;

    @Override
    public void run(String... args) {
        AESEncryptHandler.register(aesUtil);
    }
}
