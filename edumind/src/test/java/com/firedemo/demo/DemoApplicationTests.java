package com.firedemo.demo;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * Spring 上下文加载冒烟测试 — 需要完整基础设施（PostgreSQL/Redis/MinIO/OpenClaw）。
 * CI 环境不具备这些依赖，通过 BaseIntegrationTest 覆盖集成测试场景。
 */
@SpringBootTest
@Disabled("需要完整基础设施 — 由 BaseIntegrationTest 子类覆盖集成测试")
class DemoApplicationTests {

    @Test
    void contextLoads() {
    }

}
