package com.firedemo.demo.common;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * 集成测试基类 — 自动选择数据源：
 * <ol>
 *   <li><b>GitHub Actions CI</b> — 使用 CI services 提供的 postgres，不启动 Testcontainers</li>
 *   <li><b>本地 Docker 可用</b> — 自动启动 pgvector Testcontainers</li>
 *   <li><b>无 Docker</b> — 要求外部 PostgreSQL 已启动（如 docker compose up postgres）</li>
 * </ol>
 * <p>
 * 使用方式：
 * <pre>{@code
 * class MyServiceTest extends BaseIntegrationTest {
 *     @Autowired private MyService myService;
 *     @Test void testSomething() { ... }
 * }
 * }</pre>
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
public abstract class BaseIntegrationTest {

    private static final Logger log = LoggerFactory.getLogger(BaseIntegrationTest.class);

    @LocalServerPort
    protected int port;

    private static PostgreSQLContainer<?> postgresContainer;
    private static boolean testcontainersStarted = false;

    static {
        if (isRunningInCi()) {
            log.info("[Test] CI 环境检测 — 使用 GitHub Actions services，跳过 Testcontainers");
        } else if (isDockerAvailable()) {
            try {
                log.info("[Test] Docker 可用 — 启动 Testcontainers pgvector");
                postgresContainer = new PostgreSQLContainer<>(
                        DockerImageName.parse("pgvector/pgvector:pg16")
                                .asCompatibleSubstituteFor("postgres"))
                        .withDatabaseName("edumind_test")
                        .withUsername("test")
                        .withPassword("test");
                postgresContainer.start();
                testcontainersStarted = true;
                log.info("[Test] Testcontainers pgvector 启动成功: {}", postgresContainer.getJdbcUrl());
            } catch (Exception e) {
                log.warn("[Test] Testcontainers 启动失败: {} — 回退到外部数据源", e.getMessage());
                postgresContainer = null;
            }
        } else {
            log.info("[Test] Docker 不可用 — 使用外部数据源（确保 docker compose up postgres 已启动）");
        }
    }

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        if (testcontainersStarted && postgresContainer != null) {
            registry.add("spring.datasource.url", postgresContainer::getJdbcUrl);
            registry.add("spring.datasource.username", postgresContainer::getUsername);
            registry.add("spring.datasource.password", postgresContainer::getPassword);
        }
        // 如果未用 Testcontainers（CI 模式 或 外部 DB），使用 application-test.properties
        // 中的默认值或 CLI 传入的 -Dspring.datasource.url=... 覆盖
    }

    @BeforeEach
    void baseSetUp() {
        // 子类可覆盖
    }

    @AfterEach
    void baseTearDown() {
        // 子类可覆盖
    }

    // ==================== 内部工具 ====================

    static boolean isRunningInCi() {
        return "true".equalsIgnoreCase(System.getenv("CI"))
                || "true".equalsIgnoreCase(System.getenv("GITHUB_ACTIONS"));
    }

    static boolean isDockerAvailable() {
        try {
            DockerClientFactory.instance().client();
            return true;
        } catch (Exception e) {
            return false;
        }
    }
}
