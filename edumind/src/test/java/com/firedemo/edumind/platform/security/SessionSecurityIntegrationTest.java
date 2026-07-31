package com.firedemo.edumind.platform.security;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.firedemo.edumind.auth.UserRegisterDTO;
import com.firedemo.edumind.auth.User;
import com.firedemo.edumind.auth.UserService;
import com.firedemo.edumind.support.BaseIntegrationTest;
import com.firedemo.edumind.auth.UserMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.session.data.redis.RedisIndexedSessionRepository;

import java.net.CookieManager;
import java.net.CookiePolicy;
import java.net.HttpCookie;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.WebSocket;
import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

@Tag("integration")
class SessionSecurityIntegrationTest extends BaseIntegrationTest {

    private static final String PASSWORD = "strong-password123";

    @Autowired private UserService userService;
    @Autowired private UserMapper userMapper;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private RedisIndexedSessionRepository sessionRepository;

    private String username;
    private User user;

    @BeforeEach
    void createTeacher() {
        username = "is" + UUID.randomUUID().toString().replace("-", "").substring(0, 10);
        UserRegisterDTO registration = new UserRegisterDTO();
        registration.setUsername(username);
        registration.setPassword(PASSWORD);
        registration.setEmail(username + "@school.edu");
        registration.setStatus("2");
        userService.register(registration);
        user = userMapper.selectOne(new LambdaQueryWrapper<User>()
                .eq(User::getUsername, username));
    }

    @AfterEach
    void removeTeacher() {
        sessionRepository.findByPrincipalName(username).keySet().forEach(sessionRepository::deleteById);
        if (user != null) userMapper.deleteById(user.getId());
    }

    @Test
    void loginRotatesSessionAndLogoutDeletesIt() throws Exception {
        BrowserClient browser = newBrowser();

        assertThat(login(browser).statusCode()).isEqualTo(200);
        String firstSessionId = browser.sessionId();
        assertThat(firstSessionId).isNotBlank();
        assertThat(sessionRepository.findByPrincipalName(username)).hasSize(1);

        RedisIndexedSessionRepository secondApplicationRepository =
                new RedisIndexedSessionRepository(sessionRepository.getSessionRedisOperations());
        secondApplicationRepository.setRedisKeyNamespace("edumind:sessions");
        assertThat(secondApplicationRepository.findByPrincipalName(username)).hasSize(1);

        HttpResponse<String> me = browser.get("/api/auth/me");
        assertThat(me.statusCode()).isEqualTo(200);
        assertThat(objectMapper.readTree(me.body()).path("data").path("username").asText())
                .isEqualTo(username);

        assertThat(login(browser).statusCode()).isEqualTo(200);
        String rotatedSessionId = browser.sessionId();
        assertThat(rotatedSessionId).isNotEqualTo(firstSessionId);

        assertThat(browser.post("/api/auth/logout", "{}").statusCode()).isEqualTo(200);
        assertThat(browser.get("/api/auth/me").statusCode()).isEqualTo(401);
        assertThat(sessionRepository.findByPrincipalName(username)).isEmpty();
    }

    @Test
    void disablingTeacherRevokesEveryIndexedSession() throws Exception {
        BrowserClient first = newBrowser();
        BrowserClient second = newBrowser();
        login(first);
        login(second);
        assertThat(sessionRepository.findByPrincipalName(username)).hasSize(2);

        user.setStatus(0);
        userMapper.updateById(user);

        assertThat(first.get("/api/auth/me").statusCode()).isEqualTo(401);
        assertThat(sessionRepository.findByPrincipalName(username)).isEmpty();
        assertThat(second.get("/api/auth/me").statusCode()).isEqualTo(401);
    }

    @Test
    void logoutClosesTeacherWebSocketBoundToHttpSession() throws Exception {
        BrowserClient browser = newBrowser();
        login(browser);
        StompWebSocketListener listener = new StompWebSocketListener();
        WebSocket socket = browser.openWebSocket(listener);
        try {
            socket.sendText("CONNECT\naccept-version:1.2\nhost:localhost\n\n\u0000", true).join();
            assertThat(listener.connected.await(5, TimeUnit.SECONDS)).isTrue();

            assertThat(browser.post("/api/auth/logout", "{}").statusCode()).isEqualTo(200);

            assertThat(listener.closed.await(10, TimeUnit.SECONDS)).isTrue();
            assertThat(listener.error.get()).isNull();
            assertThat(listener.closeCode.get()).isEqualTo(1008);
        } finally {
            if (!socket.isOutputClosed()) {
                socket.sendClose(WebSocket.NORMAL_CLOSURE, "test complete").join();
            }
        }
    }

    private HttpResponse<String> login(BrowserClient browser) throws Exception {
        String body = objectMapper.writeValueAsString(java.util.Map.of(
                "username", username,
                "password", PASSWORD));
        HttpResponse<String> response = browser.post("/api/auth/login", body);
        JsonNode envelope = objectMapper.readTree(response.body());
        assertThat(envelope.path("code").asInt()).isEqualTo(200);
        assertThat(envelope.path("data").has("token")).isFalse();
        assertThat(envelope.path("data").has("refreshToken")).isFalse();
        return response;
    }

    private BrowserClient newBrowser() throws Exception {
        CookieManager cookies = new CookieManager(null, CookiePolicy.ACCEPT_ALL);
        HttpClient client = HttpClient.newBuilder()
                .cookieHandler(cookies)
                .connectTimeout(Duration.ofSeconds(5))
                .build();
        BrowserClient browser = new BrowserClient(client, cookies);
        HttpResponse<String> csrf = browser.get("/api/auth/csrf");
        assertThat(csrf.statusCode()).isEqualTo(200);
        browser.csrfToken = objectMapper.readTree(csrf.body()).path("data").path("token").asText();
        assertThat(browser.csrfToken).isNotBlank();
        return browser;
    }

    private final class BrowserClient {
        private final HttpClient client;
        private final CookieManager cookies;
        private String csrfToken;

        private BrowserClient(HttpClient client, CookieManager cookies) {
            this.client = client;
            this.cookies = cookies;
        }

        private HttpResponse<String> get(String path) throws Exception {
            return client.send(HttpRequest.newBuilder(uri(path)).GET().build(),
                    HttpResponse.BodyHandlers.ofString());
        }

        private HttpResponse<String> post(String path, String body) throws Exception {
            return client.send(HttpRequest.newBuilder(uri(path))
                            .header("Content-Type", "application/json")
                            .header("X-XSRF-TOKEN", csrfToken)
                            .POST(HttpRequest.BodyPublishers.ofString(body))
                            .build(),
                    HttpResponse.BodyHandlers.ofString());
        }

        private String sessionId() {
            return cookies.getCookieStore().getCookies().stream()
                    .filter(cookie -> "EDUMIND_SESSION".equals(cookie.getName()))
                    .map(HttpCookie::getValue)
                    .findFirst()
                    .orElse(null);
        }

        private WebSocket openWebSocket(WebSocket.Listener listener) throws Exception {
            return HttpClient.newHttpClient().newWebSocketBuilder()
                    .connectTimeout(Duration.ofSeconds(5))
                    .subprotocols("v12.stomp")
                    .header("Cookie", "EDUMIND_SESSION=" + sessionId())
                    .buildAsync(URI.create("ws://127.0.0.1:" + port + "/ws/live"), listener)
                    .get(5, TimeUnit.SECONDS);
        }

        private URI uri(String path) {
            return URI.create("http://127.0.0.1:" + port + path);
        }
    }

    private static final class StompWebSocketListener implements WebSocket.Listener {
        private final CountDownLatch connected = new CountDownLatch(1);
        private final CountDownLatch closed = new CountDownLatch(1);
        private final AtomicInteger closeCode = new AtomicInteger(-1);
        private final AtomicReference<Throwable> error = new AtomicReference<>();
        private final StringBuilder text = new StringBuilder();

        @Override
        public void onOpen(WebSocket webSocket) {
            webSocket.request(1);
        }

        @Override
        public CompletionStage<?> onText(WebSocket webSocket, CharSequence data, boolean last) {
            text.append(data);
            if (last) {
                if (text.toString().startsWith("CONNECTED")) connected.countDown();
                text.setLength(0);
            }
            webSocket.request(1);
            return null;
        }

        @Override
        public CompletionStage<?> onClose(WebSocket webSocket, int statusCode, String reason) {
            closeCode.set(statusCode);
            closed.countDown();
            return null;
        }

        @Override
        public void onError(WebSocket webSocket, Throwable throwable) {
            error.set(throwable);
            connected.countDown();
            closed.countDown();
        }
    }
}
