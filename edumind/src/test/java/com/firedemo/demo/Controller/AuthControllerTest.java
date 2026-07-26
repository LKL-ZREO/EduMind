package com.firedemo.demo.Controller;

import com.firedemo.demo.DTO.UserLoginDTO;
import com.firedemo.demo.DTO.UserRegisterDTO;
import com.firedemo.demo.Entity.User;
import com.firedemo.demo.Service.UserService;
import com.firedemo.demo.VO.UserLoginVO;
import com.firedemo.demo.common.exception.BusinessException;
import com.firedemo.demo.common.exception.ErrorCode;
import com.firedemo.demo.common.result.Result;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.csrf.CsrfToken;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("AuthController — Session 认证控制器")
class AuthControllerTest {

    private UserService userService;
    private SecurityContextRepository securityContextRepository;
    private AuthController controller;

    @BeforeEach
    void setUp() {
        userService = mock(UserService.class);
        securityContextRepository = mock(SecurityContextRepository.class);
        controller = new AuthController(userService, securityContextRepository);
    }

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Nested
    class Register {

        @Test
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
        void shouldThrowWhenUserExists() {
            UserRegisterDTO dto = new UserRegisterDTO();
            doThrow(new BusinessException(ErrorCode.USER_ALREADY_EXISTS))
                    .when(userService).register(any());

            assertThatThrownBy(() -> controller.register(dto))
                    .isInstanceOf(BusinessException.class);
        }
    }

    @Test
    void csrfEndpointReturnsFrameworkToken() {
        CsrfToken token = mock(CsrfToken.class);
        when(token.getToken()).thenReturn("csrf-token");
        when(token.getHeaderName()).thenReturn("X-XSRF-TOKEN");

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setAttribute("_csrf", token);
        Result<java.util.Map<String, String>> result = controller.csrf(request);

        assertThat(result.getData())
                .containsEntry("token", "csrf-token")
                .containsEntry("headerName", "X-XSRF-TOKEN");
    }

    @Nested
    class Login {

        @Test
        void createsTeacherSecurityContextAndRotatesExistingSession() {
            UserLoginDTO dto = loginDto();
            UserLoginVO vo = loginVo();
            when(userService.login(dto)).thenReturn(vo);
            MockHttpServletRequest request = new MockHttpServletRequest();
            MockHttpSession existing = (MockHttpSession) request.getSession(true);
            String oldSessionId = existing.getId();
            MockHttpServletResponse response = new MockHttpServletResponse();

            Result<UserLoginVO> result = controller.login(dto, request, response);

            assertThat(result.getData().getUsername()).isEqualTo("teacher1");
            assertThat(request.getSession().getId()).isNotEqualTo(oldSessionId);
            assertThat(result.getData().getClass().getDeclaredFields())
                    .extracting(java.lang.reflect.Field::getName)
                    .doesNotContain("token", "refreshToken", "expiresIn");

            ArgumentCaptor<SecurityContext> captor = ArgumentCaptor.forClass(SecurityContext.class);
            verify(securityContextRepository).saveContext(captor.capture(), any(), any());
            assertThat(captor.getValue().getAuthentication().getName()).isEqualTo("teacher1");
            assertThat(captor.getValue().getAuthentication().getDetails()).isEqualTo(1L);
            assertThat(captor.getValue().getAuthentication().getAuthorities())
                    .extracting("authority")
                    .containsExactly("ROLE_TEACHER");
        }

        @Test
        void propagatesLoginFailure() {
            UserLoginDTO dto = loginDto();
            when(userService.login(dto)).thenThrow(new BusinessException(ErrorCode.PASSWORD_ERROR));

            assertThatThrownBy(() -> controller.login(
                    dto, new MockHttpServletRequest(), new MockHttpServletResponse()))
                    .isInstanceOf(BusinessException.class);
        }
    }

    @Test
    void meRestoresUserFromAuthenticatedSession() {
        var authentication = org.springframework.security.authentication
                .UsernamePasswordAuthenticationToken.authenticated(
                        "teacher1", null,
                        java.util.List.of(new org.springframework.security.core.authority
                                .SimpleGrantedAuthority("ROLE_TEACHER")));
        authentication.setDetails(1L);
        User user = new User();
        user.setId(1L);
        user.setUsername("teacher1");
        user.setEmail("teacher@school.edu");
        user.setStatus(2);
        when(userService.getById(1L)).thenReturn(user);

        Result<UserLoginVO> result = controller.me(authentication);

        assertThat(result.getCode()).isEqualTo(200);
        assertThat(result.getData().getUsername()).isEqualTo("teacher1");
    }

    @Test
    void logoutInvalidatesCurrentSession() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpSession session = (MockHttpSession) request.getSession(true);

        Result<Void> result = controller.logout(request);

        assertThat(result.getCode()).isEqualTo(200);
        assertThat(session.isInvalid()).isTrue();
    }

    private UserLoginDTO loginDto() {
        UserLoginDTO dto = new UserLoginDTO();
        dto.setUsername("teacher1");
        dto.setPassword("password123");
        return dto;
    }

    private UserLoginVO loginVo() {
        return UserLoginVO.builder()
                .id(1L)
                .username("teacher1")
                .email("teacher@school.edu")
                .sessionId("chat-session")
                .build();
    }
}
