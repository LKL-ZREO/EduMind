package com.firedemo.edumind.platform.security.crypto;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AesUtilTest {

    @Test
    void encryptsAndDecryptsWithA32ByteKey() {
        AesUtil aesUtil = new AesUtil(encodedKey("edumind-test-key-32-bytes-fixed!"));

        String encrypted = aesUtil.encrypt("teacher@example.edu");

        assertThat(encrypted).isNotEqualTo("teacher@example.edu");
        assertThat(aesUtil.decrypt(encrypted)).isEqualTo("teacher@example.edu");
    }

    @Test
    void rejectsKeysThatAreNot32BytesAfterDecoding() {
        String invalidKey = encodedKey("this-key-is-not-32-bytes");

        assertThatThrownBy(() -> new AesUtil(invalidKey))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("exactly 32 bytes");
    }

    @Test
    void rejectsInvalidBase64() {
        assertThatThrownBy(() -> new AesUtil("not valid Base64!"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("valid Base64");
    }

    private static String encodedKey(String value) {
        return Base64.getEncoder().encodeToString(value.getBytes(StandardCharsets.UTF_8));
    }
}
