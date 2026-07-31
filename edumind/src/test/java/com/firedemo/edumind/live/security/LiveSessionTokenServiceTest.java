package com.firedemo.edumind.live.security;

import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

class LiveSessionTokenServiceTest {

    private static final String SECRET =
            "test-live-session-secret-key-that-is-longer-than-thirty-two-bytes";

    @Test
    void issuesTokenScopedToOneStudentAndClassroom() {
        LiveSessionTokenService service = new LiveSessionTokenService(SECRET, Duration.ofHours(2));

        String token = service.issue("S001", "张三", 99L);

        assertThat(service.parse(token)).contains(new ClassroomStudentPrincipal("S001", "张三", 99L));
    }

    @Test
    void rejectsTamperedAndExpiredTokens() throws Exception {
        LiveSessionTokenService service = new LiveSessionTokenService(SECRET, Duration.ofMillis(1));
        String token = service.issue("S001", "张三", 99L);
        Thread.sleep(5);

        assertThat(service.parse(token)).isEmpty();
        assertThat(service.parse(token + "tampered")).isEmpty();
    }
}
