package com.firedemo.demo.utils;

import com.firedemo.demo.Entity.User;
import com.firedemo.demo.Service.UserService;
import com.firedemo.demo.Service.UserSessionService;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TeacherSessionContextFilterTest {

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void restoresTrustedRequestAttributesForActiveTeacher() throws Exception {
        UserService userService = mock(UserService.class);
        UserSessionService sessions = mock(UserSessionService.class);
        TeacherSessionContextFilter filter = new TeacherSessionContextFilter(userService, sessions);
        User user = user(10L, 2);
        when(userService.getById(10L)).thenReturn(user);
        SecurityContextHolder.getContext().setAuthentication(teacherAuthentication());
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(request, response, chain);

        assertThat(request.getAttribute("userId")).isEqualTo(10L);
        assertThat(request.getAttribute("status")).isEqualTo(2);
        verify(chain).doFilter(request, response);
        verify(sessions, never()).revokeByUsername("teacher");
    }

    @Test
    void disabledTeacherRevokesIndexedSessionsAndReturns401() throws Exception {
        UserService userService = mock(UserService.class);
        UserSessionService sessions = mock(UserSessionService.class);
        TeacherSessionContextFilter filter = new TeacherSessionContextFilter(userService, sessions);
        when(userService.getById(10L)).thenReturn(user(10L, 0));
        SecurityContextHolder.getContext().setAuthentication(teacherAuthentication());
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpSession session = (MockHttpSession) request.getSession(true);
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(session.isInvalid()).isTrue();
        verify(sessions).revokeByUsername("teacher");
        verify(chain, never()).doFilter(request, response);
    }

    private UsernamePasswordAuthenticationToken teacherAuthentication() {
        var authentication = UsernamePasswordAuthenticationToken.authenticated(
                "teacher", null, List.of(new SimpleGrantedAuthority("ROLE_TEACHER")));
        authentication.setDetails(10L);
        return authentication;
    }

    private User user(Long id, int status) {
        User user = new User();
        user.setId(id);
        user.setStatus(status);
        return user;
    }
}
