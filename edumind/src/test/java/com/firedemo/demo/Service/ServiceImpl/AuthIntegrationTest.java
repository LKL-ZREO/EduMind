package com.firedemo.demo.Service.ServiceImpl;

import com.firedemo.demo.DTO.UserLoginDTO;
import com.firedemo.demo.DTO.UserRegisterDTO;
import com.firedemo.demo.Entity.User;
import com.firedemo.demo.Service.TokenService;
import com.firedemo.demo.Service.UserService;
import com.firedemo.demo.VO.UserLoginVO;
import com.firedemo.demo.common.BaseIntegrationTest;
import com.firedemo.demo.common.exception.BusinessException;
import com.firedemo.demo.mapper.UserMapper;
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
    @Autowired private TokenService tokenService;

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
        @DisplayName("注册后立即登录成功，返回 JWT + 刷新令牌")
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

            assertThat(vo.getToken()).isNotBlank();
            assertThat(vo.getRefreshToken()).isNotBlank();
            assertThat(vo.getExpiresIn()).isPositive();
            assertThat(vo.getUsername()).isEqualTo("itest_teacher1");
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

    @Nested
    @DisplayName("刷新令牌")
    class RefreshToken {

        @Test
        @DisplayName("有效刷新令牌 → 换取新 token，旧 token 立即失效")
        void shouldRefreshAndInvalidate() {
            UserRegisterDTO reg = new UserRegisterDTO();
            reg.setUsername("itest_refresh");
            reg.setPassword("strong-password123");
            userService.register(reg);

            UserLoginDTO login = new UserLoginDTO();
            login.setUsername("itest_refresh");
            login.setPassword("strong-password123");
            UserLoginVO vo = userService.login(login);

            Long userId = tokenService.consumeRefreshToken(vo.getRefreshToken());
            assertThat(userId).isEqualTo(vo.getId());

            // 同一个刷新令牌不能再用
            Long second = tokenService.consumeRefreshToken(vo.getRefreshToken());
            assertThat(second).isNull();
        }
    }

    @Nested
    @DisplayName("token 黑名单")
    class Blacklist {

        @Test
        @DisplayName("加入黑名单后 isBlacklisted 返回 true")
        void shouldBlacklist() {
            tokenService.blacklist("itest.fake.token.test", 60);
            assertThat(tokenService.isBlacklisted("itest.fake.token.test")).isTrue();
        }

        @Test
        @DisplayName("未加入黑名单 → 返回 false")
        void shouldNotBlacklist() {
            assertThat(tokenService.isBlacklisted("itest.never.blacklisted")).isFalse();
        }
    }
}
