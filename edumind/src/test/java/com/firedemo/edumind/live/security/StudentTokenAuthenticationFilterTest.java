package com.firedemo.edumind.live.security;

import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class StudentTokenAuthenticationFilterTest {

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void createsStudentAuthenticationFromClassroomToken() throws Exception {
        LiveSessionTokenService tokenService = mock(LiveSessionTokenService.class);
        when(tokenService.parse("classroom-token")).thenReturn(Optional.of(
                new ClassroomStudentPrincipal("S001", "张三", 99L)));
        StudentTokenAuthenticationFilter filter = new StudentTokenAuthenticationFilter(tokenService);
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer classroom-token");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(request, response, chain);

        assertThat(SecurityContextHolder.getContext().getAuthentication().getPrincipal())
                .isEqualTo(new ClassroomStudentPrincipal("S001", "张三", 99L));
        assertThat(request.getAttribute("sessionId")).isEqualTo(99L);
        verify(chain).doFilter(request, response);
    }

    @Test
    void rejectsInvalidBearerToken() throws Exception {
        LiveSessionTokenService tokenService = mock(LiveSessionTokenService.class);
        when(tokenService.parse("invalid")).thenReturn(Optional.empty());
        StudentTokenAuthenticationFilter filter = new StudentTokenAuthenticationFilter(tokenService);
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer invalid");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(401);
        verify(chain, never()).doFilter(request, response);
    }
}
