package com.firedemo.demo.Service.ServiceImpl;

import com.firedemo.demo.DTO.UserLoginDTO;
import com.firedemo.demo.DTO.UserRegisterDTO;
import com.firedemo.demo.Entity.User;
import com.firedemo.demo.VO.UserLoginVO;
import com.firedemo.demo.common.exception.BusinessException;
import com.firedemo.demo.mapper.ChatHistoryMapper;
import com.firedemo.demo.mapper.UserMapper;
import com.firedemo.demo.utils.JwtUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * UserServiceImpl 纯单元测试 — 只 Mock 依赖，不启动 Spring 上下文。
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("UserServiceImpl — 用户服务")
class UserServiceImplTest {

    @Mock
    private UserMapper userMapper;
    @Mock
    private ChatHistoryMapper chatHistoryMapper;
    @Mock
    private JwtUtil jwtUtil;

    @InjectMocks
    private UserServiceImpl userService;

    // ==================== 注册 ====================

    @Nested
    @DisplayName("register — 用户注册")
    class Register {

        @Test
        @DisplayName("正常注册成功")
        void shouldRegisterSuccessfully() {
            UserRegisterDTO dto = new UserRegisterDTO();
            dto.setUsername("newteacher");
            dto.setPassword("password123");
            dto.setEmail("teacher@school.edu");
            dto.setStatus("2");

            when(userMapper.selectCount(any())).thenReturn(0L);
            doReturn(1).when(userMapper).insert(any(User.class));

            assertThatCode(() -> userService.register(dto)).doesNotThrowAnyException();

            // 验证插入的用户数据包含加密后的密码
            ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
            verify(userMapper).insert(userCaptor.capture());
            User saved = userCaptor.getValue();
            assertThat(saved.getUsername()).isEqualTo("newteacher");
            assertThat(saved.getEmail()).isEqualTo("teacher@school.edu");
            assertThat(saved.getPassword()).isNotEqualTo("password123"); // 密码已加密
            assertThat(saved.getStatus()).isEqualTo(2);
        }

        @Test
        @DisplayName("用户名已存在 → 抛出 BusinessException")
        void shouldThrowWhenUsernameExists() {
            UserRegisterDTO dto = new UserRegisterDTO();
            dto.setUsername("existinguser");
            dto.setPassword("password123");
            dto.setStatus("2");

            when(userMapper.selectCount(any())).thenReturn(1L);

            assertThatThrownBy(() -> userService.register(dto))
                    .isInstanceOf(BusinessException.class);
            verify(userMapper, never()).insert(any(User.class));
        }

        @Test
        @DisplayName("status 为空时默认为 2（老师）")
        void shouldDefaultStatusToTeacher() {
            UserRegisterDTO dto = new UserRegisterDTO();
            dto.setUsername("teacher2");
            dto.setPassword("password123");
            dto.setStatus(null); // 不传 status

            when(userMapper.selectCount(any())).thenReturn(0L);
            doReturn(1).when(userMapper).insert(any(User.class));

            userService.register(dto);

            ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
            verify(userMapper).insert(captor.capture());
            assertThat(captor.getValue().getStatus()).isEqualTo(2);
        }
    }

    // ==================== 登录 ====================

    @Nested
    @DisplayName("login — 用户登录")
    class Login {

        User existingUser;

        @BeforeEach
        void setUp() {
            existingUser = new User();
            existingUser.setId(1L);
            existingUser.setUsername("teacher1");
            existingUser.setEmail("t1@school.edu");
            existingUser.setPassword(com.firedemo.demo.utils.PasswordUtil.encode("correct-password"));
            existingUser.setStatus(2); // 正常状态
        }

        @Test
        @DisplayName("正常登录返回 UserLoginVO（含 JWT 和 sessionId）")
        void shouldLoginSuccessfully() {
            UserLoginDTO dto = new UserLoginDTO();
            dto.setUsername("teacher1");
            dto.setPassword("correct-password");

            when(userMapper.selectOne(any())).thenReturn(existingUser);
            when(jwtUtil.generateToken(1L, "teacher1", 2)).thenReturn("jwt-token-abc");
            when(chatHistoryMapper.selectSessionIdsByUserId(1L)).thenReturn(List.of("existing-session-123"));

            UserLoginVO result = userService.login(dto);

            assertThat(result).isNotNull();
            assertThat(result.getId()).isEqualTo(1L);
            assertThat(result.getUsername()).isEqualTo("teacher1");
            assertThat(result.getToken()).isEqualTo("jwt-token-abc");
            assertThat(result.getSessionId()).isEqualTo("existing-session-123");
        }

        @Test
        @DisplayName("新用户（无历史 session）→ 自动生成 sessionId")
        void shouldCreateNewSessionIdForNewUser() {
            UserLoginDTO dto = new UserLoginDTO();
            dto.setUsername("teacher1");
            dto.setPassword("correct-password");

            when(userMapper.selectOne(any())).thenReturn(existingUser);
            when(jwtUtil.generateToken(anyLong(), anyString(), any())).thenReturn("jwt-token-xyz");
            when(chatHistoryMapper.selectSessionIdsByUserId(1L)).thenReturn(Collections.emptyList());

            UserLoginVO result = userService.login(dto);

            assertThat(result.getSessionId()).isNotNull();
            assertThat(result.getSessionId()).startsWith("session_");
        }

        @Test
        @DisplayName("用户不存在 → 抛出 BusinessException")
        void shouldThrowWhenUserNotFound() {
            UserLoginDTO dto = new UserLoginDTO();
            dto.setUsername("nonexistent");
            dto.setPassword("whatever");

            when(userMapper.selectOne(any())).thenReturn(null);

            assertThatThrownBy(() -> userService.login(dto))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("用户不存在");
        }

        @Test
        @DisplayName("密码错误 → 抛出 BusinessException")
        void shouldThrowWhenPasswordWrong() {
            UserLoginDTO dto = new UserLoginDTO();
            dto.setUsername("teacher1");
            dto.setPassword("wrong-password");

            when(userMapper.selectOne(any())).thenReturn(existingUser);

            assertThatThrownBy(() -> userService.login(dto))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("密码错误");
        }

        @Test
        @DisplayName("账号被禁用（status=0）→ 抛出 BusinessException")
        void shouldThrowWhenAccountDisabled() {
            existingUser.setStatus(0);
            UserLoginDTO dto = new UserLoginDTO();
            dto.setUsername("teacher1");
            dto.setPassword("correct-password");

            when(userMapper.selectOne(any())).thenReturn(existingUser);

            assertThatThrownBy(() -> userService.login(dto))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("账号已被禁用");
        }
    }

    // ==================== 查询 ====================

    @Nested
    @DisplayName("getById — 根据 ID 查询")
    class GetById {

        @Test
        @DisplayName("存在则返回 User")
        void shouldReturnUserWhenFound() {
            User user = new User();
            user.setId(1L);
            user.setUsername("teacher1");
            when(userMapper.selectById(1L)).thenReturn(user);

            User result = userService.getById(1L);

            assertThat(result).isNotNull();
            assertThat(result.getUsername()).isEqualTo("teacher1");
        }

        @Test
        @DisplayName("不存在则返回 null")
        void shouldReturnNullWhenNotFound() {
            when(userMapper.selectById(999L)).thenReturn(null);

            User result = userService.getById(999L);

            assertThat(result).isNull();
        }
    }
}
