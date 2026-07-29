package com.firedemo.demo.live.security;

import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

class StudentDeviceTokenServiceTest {

    private static final String SECRET =
            "test-live-session-secret-key-that-is-longer-than-thirty-two-bytes";

    @Test
    void remembersStudentWithoutStoringNameInTheBrowser() {
        StudentDeviceTokenService service =
                new StudentDeviceTokenService(SECRET, Duration.ofDays(180), false);

        String token = service.issue("S001");

        assertThat(service.parse(token)).contains("S001");
        assertThat(service.bindingCookie("S001").isHttpOnly()).isTrue();
        assertThat(service.bindingCookie("S001").getSameSite()).isEqualTo("Lax");
    }

    @Test
    void rejectsClassroomTokensAndClearsTheDeviceCookie() {
        StudentDeviceTokenService service =
                new StudentDeviceTokenService(SECRET, Duration.ofDays(180), false);
        LiveSessionTokenService classroomTokens =
                new LiveSessionTokenService(SECRET, Duration.ofHours(2));

        assertThat(service.parse(classroomTokens.issue("S001", "张三", 99L))).isEmpty();
        assertThat(service.clearingCookie().getMaxAge()).isZero();
    }
}
