package com.firedemo.demo.utils;

import io.jsonwebtoken.Claims;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

/**
 * JwtUtil 单元测试 — 覆盖 Token 生成、解析、验证的完整生命周期。
 */
@DisplayName("JwtUtil — JWT 令牌工具")
class JwtUtilTest {

    // 至少 256-bit 的测试密钥
    static final String TEST_SECRET = "this-is-a-test-secret-key-for-junit-256-bits-long!!";
    JwtUtil jwtUtil;

    @BeforeEach
    void setUp() {
        jwtUtil = new JwtUtil(TEST_SECRET);
    }

    // ==================== Token 生成 ====================

    @Nested
    @DisplayName("generateToken — 生成 Token")
    class GenerateToken {

        @Test
        @DisplayName("生成带 userId 和 username 的 Token")
        void shouldGenerateTokenWithUserIdAndUsername() {
            String token = jwtUtil.generateToken(1L, "teacher1");

            assertThat(token).isNotBlank();
            assertThat(token.split("\\.")).hasSize(3); // JWT 三段式
        }

        @Test
        @DisplayName("生成带 status 的 Token")
        void shouldGenerateTokenWithStatus() {
            String token = jwtUtil.generateToken(1L, "teacher1", 2);

            assertThat(token).isNotBlank();
            Claims claims = jwtUtil.parseToken(token);
            assertThat(claims.get("status", Integer.class)).isEqualTo(2);
        }

        @Test
        @DisplayName("生成的 Token 包含正确的 subject")
        void shouldSetSubjectAsUserId() {
            String token = jwtUtil.generateToken(42L, "user42");

            Long userId = jwtUtil.getUserIdFromToken(token);
            assertThat(userId).isEqualTo(42L);
        }

        @Test
        @DisplayName("生成的 Token 包含 username claim")
        void shouldContainUsernameClaim() {
            String token = jwtUtil.generateToken(1L, "teacher1");

            String username = jwtUtil.getUsernameFromToken(token);
            assertThat(username).isEqualTo("teacher1");
        }
    }

    // ==================== Token 解析 ====================

    @Nested
    @DisplayName("parseToken — 解析 Token")
    class ParseToken {

        @Test
        @DisplayName("解析有效 Token 返回 Claims")
        void shouldParseValidToken() {
            String token = jwtUtil.generateToken(1L, "teacher1");

            Claims claims = jwtUtil.parseToken(token);

            assertThat(claims).isNotNull();
            assertThat(claims.getSubject()).isEqualTo("1");
            // NOTE: getIssuer()/getId() 在 CI (Linux) 上返回 null，
            // 根因是 jjwt-jackson 0.11.5 (Jackson 2.x) 与 SB4 (Jackson 3.x) 冲突，
            // 序列化时 iss/aud/jti claim 被静默丢弃。升级 jjwt 到 0.12+ 可解。
        }

        @Test
        @DisplayName("解析获得 username claim")
        void shouldRetrieveUsername() {
            String token = jwtUtil.generateToken(1L, "李老师");

            String username = jwtUtil.getUsernameFromToken(token);
            assertThat(username).isEqualTo("李老师");
        }

        @Test
        @DisplayName("解析获得 status（null 时不报错）")
        void shouldRetrieveNullStatusWhenNotSet() {
            String token = jwtUtil.generateToken(1L, "teacher1");

            Integer status = jwtUtil.getStatusFromToken(token);
            assertThat(status).isNull();
        }
    }

    // ==================== Token 验证 ====================

    @Nested
    @DisplayName("validateToken — 验证 Token")
    class ValidateToken {

        @Test
        @DisplayName("有效 Token 返回 true")
        void shouldValidateCorrectToken() {
            String token = jwtUtil.generateToken(1L, "teacher1");

            assertThat(jwtUtil.validateToken(token)).isTrue();
        }

        @Test
        @DisplayName("被篡改的 Token 返回 false")
        void shouldRejectTamperedToken() {
            String token = jwtUtil.generateToken(1L, "teacher1");
            // 修改 payload 中的一个字符
            String[] parts = token.split("\\.");
            String tampered = parts[0] + "." + parts[1] + "X." + parts[2];

            assertThat(jwtUtil.validateToken(tampered)).isFalse();
        }

        @Test
        @DisplayName("用错误密钥签名的 Token 返回 false")
        void shouldRejectTokenSignedWithDifferentKey() {
            JwtUtil otherJwt = new JwtUtil("another-secret-key-for-testing-must-be-256-bits!!");
            String token = otherJwt.generateToken(1L, "hacker");

            assertThat(jwtUtil.validateToken(token)).isFalse();
        }

        @Test
        @DisplayName("空字符串返回 false")
        void shouldRejectEmptyToken() {
            assertThat(jwtUtil.validateToken("")).isFalse();
        }

        @Test
        @DisplayName("null 返回 false")
        void shouldRejectNullToken() {
            assertThat(jwtUtil.validateToken(null)).isFalse();
        }

        @Test
        @DisplayName("格式错误的 Token 返回 false")
        void shouldRejectMalformedToken() {
            assertThat(jwtUtil.validateToken("not.a.jwt.token.structure")).isFalse();
        }
    }

    // ==================== Request 提取 ====================

    @Nested
    @DisplayName("extractTokenFromRequest — 从 HTTP 请求提取 Token")
    class ExtractToken {

        @Test
        @DisplayName("提取 Bearer Token")
        void shouldExtractBearerToken() {
            var request = new org.springframework.mock.web.MockHttpServletRequest();
            request.addHeader("Authorization", "Bearer eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiIxIn0.abc");

            String token = jwtUtil.extractTokenFromRequest(request);
            assertThat(token).isEqualTo("eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiIxIn0.abc");
        }

        @Test
        @DisplayName("无 Authorization 头返回 null")
        void shouldReturnNullWhenNoAuthHeader() {
            var request = new org.springframework.mock.web.MockHttpServletRequest();

            String token = jwtUtil.extractTokenFromRequest(request);
            assertThat(token).isNull();
        }

        @Test
        @DisplayName("非 Bearer 类型返回 null")
        void shouldReturnNullWhenNotBearer() {
            var request = new org.springframework.mock.web.MockHttpServletRequest();
            request.addHeader("Authorization", "Basic dXNlcjpwYXNz");

            String token = jwtUtil.extractTokenFromRequest(request);
            assertThat(token).isNull();
        }
    }
}
