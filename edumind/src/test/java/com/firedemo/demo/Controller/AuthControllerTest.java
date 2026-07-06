package com.firedemo.demo.Controller;

import com.firedemo.demo.DTO.UserLoginDTO;
import com.firedemo.demo.DTO.UserRegisterDTO;
import com.firedemo.demo.Service.UserService;
import com.firedemo.demo.VO.UserLoginVO;
import com.firedemo.demo.common.exception.BusinessException;
import com.firedemo.demo.common.exception.ErrorCode;
import com.firedemo.demo.common.result.Result;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * AuthController 纯单元测试 — 手动构造 Controller，Mock Service 层。
 */
@DisplayName("AuthController — 认证控制器")
class AuthControllerTest {

    private UserService userService;
    private AuthController controller;

    @BeforeEach
    void setUp() {
        userService = mock(UserService.class);
        controller = new AuthController(userService);
    }

    @Nested
    @DisplayName("POST /api/auth/register")
    class Register {

        @Test
        @DisplayName("正常注册 → 200")
        void shouldRegisterSuccessfully() {
            UserRegisterDTO dto = new UserRegisterDTO();
            dto.setUsername("newteacher");
            dto.setPassword("password123");
            dto.setEmail("teacher@school.edu");

            doNothing().when(userService).register(any());

            Result<Void> result = controller.register(dto);

            assertThat(result.getCode()).isEqualTo(200);
            verify(userService).register(dto);
        }

        @Test
        @DisplayName("用户名已存在 → 抛出异常")
        void shouldThrowWhenUserExists() {
            UserRegisterDTO dto = new UserRegisterDTO();
            dto.setUsername("existing");
            dto.setPassword("password123");

            doThrow(new BusinessException(ErrorCode.USER_ALREADY_EXISTS))
                    .when(userService).register(any());

            assertThatThrownBy(() -> controller.register(dto))
                    .isInstanceOf(BusinessException.class)
                    .extracting("code")
                    .isEqualTo(ErrorCode.USER_ALREADY_EXISTS.getCode());
        }
    }

    @Nested
    @DisplayName("POST /api/auth/login")
    class Login {

        @Test
        @DisplayName("正常登录 → 200 返回 JWT")
        void shouldLoginSuccessfully() {
            UserLoginDTO dto = new UserLoginDTO();
            dto.setUsername("teacher1");
            dto.setPassword("password123");

            UserLoginVO vo = UserLoginVO.builder()
                    .id(1L)
                    .username("teacher1")
                    .email("t1@school.edu")
                    .token("jwt-token-abc")
                    .sessionId("session-xyz")
                    .build();

            when(userService.login(any())).thenReturn(vo);

            Result<UserLoginVO> result = controller.login(dto);

            assertThat(result.getCode()).isEqualTo(200);
            assertThat(result.getData().getToken()).isEqualTo("jwt-token-abc");
            assertThat(result.getData().getUsername()).isEqualTo("teacher1");
            assertThat(result.getData().getSessionId()).isEqualTo("session-xyz");
        }

        @Test
        @DisplayName("用户不存在 → 抛出异常")
        void shouldThrowWhenUserNotFound() {
            UserLoginDTO dto = new UserLoginDTO();
            dto.setUsername("ghost");
            dto.setPassword("password");

            when(userService.login(any()))
                    .thenThrow(new BusinessException(ErrorCode.USER_NOT_FOUND));

            assertThatThrownBy(() -> controller.login(dto))
                    .isInstanceOf(BusinessException.class);
        }

        @Test
        @DisplayName("密码错误 → 抛出异常")
        void shouldThrowWhenPasswordWrong() {
            UserLoginDTO dto = new UserLoginDTO();
            dto.setUsername("teacher1");
            dto.setPassword("wrongpass");

            when(userService.login(any()))
                    .thenThrow(new BusinessException(ErrorCode.PASSWORD_ERROR));

            assertThatThrownBy(() -> controller.login(dto))
                    .isInstanceOf(BusinessException.class);
        }
    }
}
