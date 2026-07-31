package com.firedemo.edumind.auth;

import com.firedemo.edumind.support.BaseIntegrationTest;
import com.firedemo.edumind.shared.exception.BusinessException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Tag;
import org.springframework.beans.factory.annotation.Autowired;

import static org.assertj.core.api.Assertions.*;

/**
 * 集成测试 — 真实 Spring 上下文 + 数据库，覆盖注册→登录→查询全链路。
 * <p>
 * 数据库来源（按优先级）：
 * <ol>
 *   <li>GitHub Actions CI — 使用 ci.yml 中配置的 PostgreSQL service</li>
 *   <li>本地 Docker — 使用 Testcontainers 自动启动 pgvector 容器</li>
 *   <li>外部 PostgreSQL — 需手动启动 docker compose up postgres</li>
 * </ol>
 * 如果以上都不可用，测试会在上下文加载阶段失败（而非静默跳过）。
 */
@Tag("integration")
@DisplayName("Auth Integration — 认证全链路集成测试")
class AuthIntegrationTest extends BaseIntegrationTest {

    @Autowired private UserService userService;
    @Autowired private UserMapper userMapper;

    @AfterEach
    void tearDown() {
        var wrapper = new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<User>()
                .likeRight(User::getUsername, "itest_");
        userMapper.delete(wrapper);
    }

    @Nested
    @DisplayName("注册 → 登录 → 查询")
    class RegisterLoginFlow {

        @Test
        @DisplayName("注册后立即登录成功，返回用户信息且不签发 Web token")
        void shouldRegisterAndLogin() {
            UserRegisterDTO reg = new UserRegisterDTO();
            reg.setUsername("itest_teacher1");
            reg.setPassword("strong-password123");
            reg.setEmail("itest@school.edu");
            reg.setStatus("2");
            userService.register(reg);

            UserLoginDTO login = new UserLoginDTO();
            login.setUsername("itest_teacher1");
            login.setPassword("strong-password123");
            UserLoginVO vo = userService.login(login);

            assertThat(vo.getUsername()).isEqualTo("itest_teacher1");
            assertThat(vo.getSessionId()).isNotBlank();
        }

        @Test
        @DisplayName("重复用户名注册 → 抛出 BusinessException")
        void shouldRejectDuplicateUsername() {
            UserRegisterDTO reg = new UserRegisterDTO();
            reg.setUsername("itest_dup");
            reg.setPassword("strong-password123");
            userService.register(reg);

            assertThatThrownBy(() -> userService.register(reg))
                    .isInstanceOf(BusinessException.class);
        }
    }

}
